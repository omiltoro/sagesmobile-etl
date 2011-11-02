/**
 * 
 */
package org.jhuapl.edu.sages.etl.strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jhuapl.edu.sages.etl.ConnectionFactory;
import org.jhuapl.edu.sages.etl.ETLProperties;
import org.jhuapl.edu.sages.etl.PropertiesLoader;
import org.jhuapl.edu.sages.etl.SagesEtlException;
import org.jhuapl.edu.sages.etl.opencsvpods.DumbTestOpenCsvJar;

/**
 * {@link SagesOpenCsvJar} is the domain class and controls the execution of the overall ETL process. \
 * It contains an {@link ETLStrategy} object that implements most of the ETL processing logic.
 *  
 * @author POKUAM1
 * @created Nov 1, 2011
 */
public abstract class SagesOpenCsvJar {

	private static org.apache.log4j.Logger log = Logger.getLogger(SagesOpenCsvJar.class);
	
	/** The {@link ETLStrategy} object **/
	protected ETLStrategy etlStrategy;
	
	/** List of files that ETL loads into the production table **/
	protected File[] csvFiles;
	
	/** The current file that is being processed **/
	protected File currentFile;
	
	/** Current file records that the ETL will load into the production table via SQL statements **/
	protected ArrayList<String[]> currentEntries;

	/** Flag used to determine whether a file should be moved to directory that signifies successful processing **/
	protected boolean success;
	
	/** TODO: not used yet. The current file marked for deletion due to an error in processing **/
	protected File fileMarkedForDeletion;
	
	
	/** TODO: not used yet **/
	protected int currentRecNum;
	
	/** TODO: not used yet. List of files ETL encountered failure while processing **/
	protected static List<File> failedCsvFiles;
	
	
	/** csv files are loaded from inputdir and moved to outputdir after being processed successfully  */
	/** ETL looks at the input directory for files to process **/
	protected String inputdir_csvfiles;
	/** ETL moves successfully processed files to the output directory **/
	protected String outputdir_csvfiles;
	/** ETL moves unsucessfully processed files to the failed directory **/
	protected String faileddir_csvfiles;
	
	protected static final String ETL_CLEANSE_TABLE = "ETL_CLEANSE_TABLE";
	protected static final String ETL_STAGING_DB = "ETL_STAGING_DB";
	protected String src_table_name;
	protected String dst_table_name;
	protected String prod_table_name;
	
	/** maps the destination columns to their sql-datatype qualifier for generating the schema */
	protected Map<String,String> DEST_COLTYPE_MAP;
	
	//http://download.oracle.com/javase/6/docs/api/constant-values.html#java.sql.Types.TIME
	/** maps the destination columns to their java.sql.Types for setting ? parameters on prepared statements */
	protected Map<String, Integer> DEST_SQLTYPE_MAP;
	
	/** maps the source:destination columns*/
	protected Map<String, String> MAPPING_MAP;
	/** maps the destination:source columns*/
	protected Map<String, String> MAPPING_REV_MAP;
		
	/** properties holders */
	protected Properties props_etlconfig;
	protected Properties props_mappings;
	protected Properties props_dateformats;
	protected Properties props_customsql_cleanse;
	protected Properties props_customsql_staging;
	protected Properties props_customsql_final_to_prod;
		
	/** target database connection settings*/
	protected String dbms;
	protected int portNumber;
	protected String serverName;
	protected String dbName;
	protected String userName;
	protected String password;
		
	/** maps source column name to its parameter index in the source table, indexing starts at 1 */
	protected Map<String,Integer> PARAMINDX_SRC = new HashMap<String,Integer>();
	/** maps destination column name to its parameter index in the destination table, indexing starts at 1 */
	protected Map<String,Integer> PARAMINDX_DST = new HashMap<String,Integer>();
		
	/** header columns used to define the CLEANSE table schema */
	protected String[] header_src = new String[0];
	
	/** errorFlag to control what to do on certain errors */
	protected int errorFlag = 0;


	/**
	 * 
	 * @throws SagesEtlException
	 */
	public SagesOpenCsvJar() throws SagesEtlException{
		super();
		PropertiesLoader etlProperties = new ETLProperties();
		etlProperties.loadEtlProperties();
		initializeProperties((ETLProperties) etlProperties);
	}
	
	public void setEtlStrategy (ETLStrategy strategy){
		etlStrategy = strategy;
	}
	
	public void extractHeaderColumns(SagesOpenCsvJar socj) throws FileNotFoundException, IOException{
		etlStrategy.extractHeaderColumns(socj);
	};
	
	String[] determineHeaderColumns(File file) throws FileNotFoundException, IOException{
		return etlStrategy.determineHeaderColumns(file);
	};

	public Savepoint buildCleanseTable(Connection c, SagesOpenCsvJar socj, Savepoint save1) throws SQLException,SagesEtlException{
		return etlStrategy.buildCleanseTable(c, socj, save1);
	};
	
	public Savepoint buildStagingTable(Connection c, SagesOpenCsvJar socj, Savepoint save1) throws SQLException, SagesEtlException{
		return etlStrategy.buildStagingTable(c, socj, save1);
	};

	
	public void generateSourceDestMappings(SagesOpenCsvJar socj){
		etlStrategy.generateSourceDestMappings(socj);
	}
	
	public void setAndExecuteInsertIntoCleansingTablePreparedStatement(
			Connection c, SagesOpenCsvJar socj,
			ArrayList<String[]> entries_rawdata, Savepoint save2,
			PreparedStatement ps_INSERT_CLEANSE) throws SQLException {
		etlStrategy.setAndExecuteInsertIntoCleansingTablePreparedStatement(c, socj, entries_rawdata, save2, ps_INSERT_CLEANSE);
	}
	
    public String buildInsertIntoCleansingTableSql(Connection c, SagesOpenCsvJar socj) throws SQLException {
		return etlStrategy.buildInsertIntoCleansingTableSql(c, socj);
	}

	public void copyFromCleanseToStaging(Connection c, SagesOpenCsvJar socj, Savepoint save2) throws SQLException, SagesEtlException {
		etlStrategy.copyFromCleanseToStaging(c, socj, save2);
	}
	
	public int errorCleanup(SagesOpenCsvJar socj, Savepoint savepoint, Connection connection, File currentCsv, String failedDirPath, Exception e){
		return etlStrategy.errorCleanup(socj, savepoint, connection, currentCsv, failedDirPath, e);
	}
	
	String addFlagColumn(String tableToModify){
		return etlStrategy.addFlagColumn(tableToModify);
	};
	

	public void alterCleanseTableAddFlagColumn(Connection c, Savepoint save1, Savepoint createCleanseSavepoint) throws SQLException, SagesEtlException{
		etlStrategy.alterCleanseTableAddFlagColumn(c, save1, createCleanseSavepoint);
	};

	public void alterStagingTableAddFlagColumn(Connection c, Savepoint save1, Savepoint createCleanseSavepoint) throws SQLException, SagesEtlException{
		etlStrategy.alterStagingTableAddFlagColumn(c, save1, createCleanseSavepoint);
	}



	
	/** Protected and helper methods **/ 

	/**
	 * Initializes the {@link SagesOpenCsvJar}' s etl properties
	 * 
	 * @param etlProperties - these are configured by updating the set of ETL properties files
	 * @throws SagesEtlException - if property doesn't exist, or issue loading properties file occurs
	 */
	protected void initializeProperties(ETLProperties etlProperties) throws SagesEtlException {
		this.props_etlconfig = etlProperties.getProps_etlconfig();
		this.props_mappings = etlProperties.getProps_mappings();
		this.props_dateformats = etlProperties.getProps_dateformats();
		this.props_customsql_cleanse = etlProperties.getProps_customsql_cleanse();
		this.props_customsql_staging = etlProperties.getProps_customsql_staging();
		this.props_customsql_final_to_prod = etlProperties.getProps_customsql_final_to_prod();
		this.dbms = etlProperties.getDbms();
		this.portNumber = etlProperties.getPortNumber();
		this.userName = etlProperties.getUserName();
		this.password = etlProperties.getPassword();
		this.serverName = etlProperties.getServerName();
		this.dbName = etlProperties.getDbName();
		
		this.inputdir_csvfiles = props_etlconfig.getProperty("csvinputdir");
		this.outputdir_csvfiles = props_etlconfig.getProperty("csvoutputdir");
		this.faileddir_csvfiles = props_etlconfig.getProperty("csvfaileddir");
	}
	
	/**
	 * Establishes database connection to the target database
	 * @return Connection
	 * @throws SQLException
	 */
	public Connection getConnection() throws SagesEtlException {
		try {
			return ConnectionFactory.createConnection(this.dbms, this.serverName, this.dbName,
					this.userName, this.password, this.portNumber);
		} catch (SQLException e){
			throw abort("Sorry, failed to establish database connection", e);
		}
    }
	
	/**
	 * Copies file to the designated destination directory, and then deletes it from its 
	 * original location. On failure, copied to FAILED directory, On success copied to OUT directory 
	 * @param file
	 * @param destinationDir
	 * @throws IOException
	 */
	protected static void etlMoveFile(File file, File destinationDir)throws IOException {
		Date date = new Date();
		long dtime = date.getTime();

		/** Move file to new dir */
		FileUtils.copyFile(file, new File(destinationDir, dtime + "_"+ file.getName()));
		FileUtils.forceDelete(file);
	}

	/**
	 * @param socj_dumb
	 * @param c
	 * @throws SQLException
	 */
	protected static void runCustomSql(Connection c, Properties customSql, String targetTableName) throws SQLException {
		//Properties customCleanseSqlprops = socj_dumb.props_customsql_cleanse;
		Properties customCleanseSqlprops = customSql;
		int numSql = customCleanseSqlprops.size();
		for (int i = 1; i <= numSql; i++) {
			String sql = customCleanseSqlprops.getProperty(String.valueOf(i));
			sql = sql.replace("$table", targetTableName);
			log.debug("CUSTOM SQL: " + sql);
			PreparedStatement ps = c.prepareStatement(sql);
			ps.execute();
		}
	}
	
	/**
	 * returns a SagesEtlException that wraps the original exception	
	 * @param msg SAGES ETL message to display
	 * @param e the original exception
	 * @return SagesEtlException
	 */
	public static SagesEtlException abort(String msg, Throwable e){
		return new SagesEtlException(e.getMessage(), e);
	}
	
}
