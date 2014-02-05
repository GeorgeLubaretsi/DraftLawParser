package draftlawparser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.ListFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

/**
 * This program parses a Google Spreadsheet that contains information about draft laws, 
 * and creates SQL scripts in order to insert this information in a database.
 * 
 * @author TI Georgia
 *
 */
public class Parser {

	public static final String GOOGLE_ACCOUNT_USERNAME = "*****";
	public static final String GOOGLE_ACCOUNT_PASSWORD = "*****";

	public static final String GOOGLE_SPREADSHEET_TITLE = "*****";

	public static final String SPREADSHEET_URL = "*****";

	public static final LinkedList<String> colNames = new LinkedList<String>(Arrays.asList("registrationDate","registrationNumber","draftLawType",
			"primaryDraftLaw","draftLawTitle","initiator","author","description","firstCommitteeHearing","firstPlenaryHearing","secondCommitteeHearing",
			"secondPlenaryHearing","thirdCommitteeHearing","thirdPlenaryHearing","lawNumber","initialLawNumber","numberDaysDiscussion"));
	
	public static final LinkedList<String> statusLabelEn = new LinkedList<String>(Arrays.asList("1st Committee","1st Plenary","2nd Committee","2nd Plenary","3rd Committee","3rd Plenary"));
	
	public static final LinkedList<String> statusLabelKa = new LinkedList<String>(Arrays.asList("პირველი კომიტეტი","პირველი პლენარული","მეორე კომიტეტი","მეორე პლენარული","მესამე კომიტეტი","მესამე პლენარული"));

	public static void main(String[] args) {
		
		String outputPath = args[0];

		SpreadsheetService service = new SpreadsheetService("tig-draftlaw-spreadsheet");

		SpreadsheetEntry spreadsheet = getGoogleSpreadsheet(service);

		if (spreadsheet != null){
			processSpreadsheet(spreadsheet, service, outputPath);
		}

	}

	private static SpreadsheetEntry getGoogleSpreadsheet(SpreadsheetService service){

		SpreadsheetEntry spreadsheetResult = null;

		/** Our view of Google Spreadsheets as an authenticated Google user. */
		service.setProtocolVersion(SpreadsheetService.Versions.V3);

		// Login and prompt the user to pick a sheet to use.
		try {
			service.setUserCredentials(GOOGLE_ACCOUNT_USERNAME, GOOGLE_ACCOUNT_PASSWORD);

			URL SPREADSHEET_FEED_URL = new URL("https://spreadsheets.google.com/feeds/spreadsheets/private/full");

			// Make a request to the API and get all spreadsheets.
			SpreadsheetFeed feed = service.getFeed(SPREADSHEET_FEED_URL, SpreadsheetFeed.class);
			List<SpreadsheetEntry> spreadsheets = feed.getEntries();

			// Iterate through all of the spreadsheets returned
			int spreadsheetIndex = 0;
			int count = 0;
			for (SpreadsheetEntry spreadsheet : spreadsheets) {
				// Print the title of this spreadsheet to the screen
				String doctitle = spreadsheet.getTitle().getPlainText();
				if (doctitle != null && doctitle.equalsIgnoreCase(GOOGLE_SPREADSHEET_TITLE)){
					spreadsheetIndex = count;
				}
				count++;
			}

			if (spreadsheets != null){
				spreadsheetResult = spreadsheets.get(spreadsheetIndex);
			}

		} catch (AuthenticationException e) {
			System.out.println("An error occured while authenticating to Google");
			e.printStackTrace();
		} catch (MalformedURLException e) {
			System.out.println("Error: given URL is malformed");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Error: Google file not found");
			e.printStackTrace();
		} catch (ServiceException e) {
			System.out.println("An error occured while instantiating SpreadsheedFeed.");
			e.printStackTrace();
		}

		return spreadsheetResult;

	}

	private static void processSpreadsheet(SpreadsheetEntry spreadsheet, SpreadsheetService service, String outputPath){
		// Make a request to the API to fetch information about all
		// worksheets in the spreadsheet.
		List<WorksheetEntry> worksheets;
		try {
			worksheets = spreadsheet.getWorksheets();

			File file = new File(outputPath+"/insertParentLaws.sql");
			File childLawFile = new File(outputPath+"/insertChildLaw.sql");

			if (!file.exists()) {
				file.createNewFile();
			}

			if (!childLawFile.exists()) {
				childLawFile.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			FileWriter childlawfw = new FileWriter(childLawFile.getAbsoluteFile());
			BufferedWriter childLawBw = new BufferedWriter(childlawfw);

			// Iterate through each worksheet in the spreadsheet.
			for (WorksheetEntry worksheet : worksheets) {
				// Get the worksheet's title, row count, and column count.
				String title = worksheet.getTitle().getPlainText();

				System.out.println(title);

				// Print the fetched information to the screen for this worksheet.
				URL listFeedUrl = worksheet.getListFeedUrl();

				ListFeed listFeed = service.getFeed(listFeedUrl, ListFeed.class);

				// Iterate through each row, printing its cell values.
				int countrow=0;
				
				for (ListEntry row : listFeed.getEntries()) {
					// Print the first column's cell value
					// Iterate over the remaining columns, and print each cell value
					if (countrow > 0){
						Map<String,String> rowData = new HashMap<String,String>();

						int countForColumn = 0;
						for (String tag : row.getCustomElements().getTags()) {
							rowData.put(colNames.get(countForColumn), row.getCustomElements().getValue(tag));
							countForColumn++;
						}

						// We need the data after the most recent published draft law (13.06.2013)
						String regDate = rowData.get("registrationDate");
						String mostRecent = "17.06.2013";
						SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
						boolean shouldBeAdded = false;
						try {
							if (regDate != null){
								Date lawDate = sdf.parse(regDate);
								Date mostRecentDate = sdf.parse(mostRecent);
								shouldBeAdded = mostRecentDate.before(lawDate);
							}
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						String registrationNumber = rowData.get("registrationNumber");

						if (shouldBeAdded){
							if (isPrimaryDraft(rowData)){
								// We're dealing with the parent law, and its hearing related information
								
								// Formatting data here, for the SQL query.
								String registrationNumberForQuery = parseDataForQuery(rowData.get("registrationNumber"),true);
								String draftLawTitle = parseDataForQuery(rowData.get("draftLawTitle"),true);
								String initiator = parseDataForQuery(rowData.get("initiator"),true);
								String author = parseDataForQuery(rowData.get("author"),true);
								String summary = parseDataForQuery(rowData.get("description"),false);
								
								String slug = registrationNumberForQuery.replaceAll("#", "").replaceAll("/", "");

								String registrationDate = null;
								if (rowData.get("registrationDate") != null){
									registrationDate = "TO_DATE('"+rowData.get("registrationDate")+"','DD.MM.YYYY')";
								}else{
									registrationDate = "TO_DATE('01.01.1970','DD.MM.YYYY')";
								}

								// Definition of the draft law's current status
								String statusEnglish = defineCurrentStatus(rowData,statusLabelEn);
								
								String statusGeorgian = defineCurrentStatus(rowData,statusLabelKa);

								// Insert main information into draftlaw_draftlaw table
								String insertQuery = "INSERT INTO draftlaw_draftlaw (bureau_date,bill_number,title,title_en,"
										+ "title_ka,initiator,initiator_en,initiator_ka,author,author_en,author_ka,status,status_en,status_ka,"
										+ "summary,summary_en,summary_ka,full_text,full_text_url,enacted_text_url,related_1,related_2,related_3," 
										+ "related_4,related_5,slug) "
										+ "VALUES ("+registrationDate+","+ registrationNumberForQuery.trim() +","+draftLawTitle+","+draftLawTitle+","
										+draftLawTitle+","+initiator+","+initiator+","+initiator+","+author+","+author+","+author
										+","+statusEnglish+","+statusEnglish+","+statusGeorgian+","+summary+","+summary+","+summary+","
										+ "'','','','','','','','',"+slug+");\n";

								bw.write(insertQuery);

								// Insert into draftlaw_draftlawdiscussion table the information related to hearing, for each draft law.
								String insertHearingQuery = "INSERT INTO draftlaw_draftlawdiscussion (draftlaw_id,date,stage,place,place_en,place_ka,passed) VALUES ";

								// Completing the insert queries with hearing info.
								writeHearingQuery(insertHearingQuery, rowData.get("firstCommitteeHearing"), registrationNumber, bw, 0);
								writeHearingQuery(insertHearingQuery, rowData.get("firstPlenaryHearing"), registrationNumber, bw, 1);
								writeHearingQuery(insertHearingQuery, rowData.get("secondCommitteeHearing"), registrationNumber, bw, 2);
								writeHearingQuery(insertHearingQuery, rowData.get("secondPlenaryHearing"), registrationNumber, bw, 3);
								writeHearingQuery(insertHearingQuery, rowData.get("thirdCommitteeHearing"), registrationNumber, bw, 4);
								writeHearingQuery(insertHearingQuery, rowData.get("thirdPlenaryHearing"), registrationNumber, bw, 5);

								bw.write("\n");
							}else{
								// We're dealing with a child law
								String registrationNumberForQuery = parseDataForQuery(rowData.get("registrationNumber"),true);
								registrationNumberForQuery = registrationNumberForQuery.trim();

								if (!registrationNumberForQuery.isEmpty()){

									String childLawTitle = parseDataForQuery(rowData.get("draftLawTitle"),true);
									String lawNumber = parseDataForQuery(rowData.get("lawNumber"),true);

									String parentIdClause = "(SELECT id FROM draftlaw_draftlaw WHERE bill_number=" + registrationNumberForQuery +")";

									String insertQuery = "INSERT INTO draftlaw_draftlawchild (parent_id,bill_number,title,law_number,title_en,title_ka,place) VALUES ("
											+ parentIdClause + ","+registrationNumberForQuery+","+childLawTitle+","+lawNumber+","+childLawTitle+","+childLawTitle+",'');";

									childLawBw.write(insertQuery + "\n");
								}
							}
						}

					}
					countrow++;
				}
			}
			bw.close();
			childLawBw.close();

			// Merging files now: insertDraftLaws.sql first, then insertChildLaw.sql because of the dependencies.
			File[] files = new File[2];
			files[0] = file;
			files[1] = childLawFile;

			File outputFile = new File(outputPath+"/insertDraftLaws.sql");

			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}

			mergeFiles(files, outputFile);

		} catch (IOException e) {
			System.out.println("Problem occured while creating SQL files");
			e.printStackTrace();
		} catch (ServiceException e) {
			System.out.println("An error occured while instantiating SpreadsheedFeed.");
			e.printStackTrace();
		}
		
		System.out.println("Done.");

	}

	/**
	 * Defines the current status of a law
	 * @param rowData
	 * @param statusLabel 
	 * @return status String
	 */
	private static String defineCurrentStatus(Map<String, String> rowData, LinkedList<String> statusLabel) {
		LinkedList<String> hearingLabels = new LinkedList<String>(Arrays.asList("firstCommitteeHearing","firstPlenaryHearing","secondCommitteeHearing","secondPlenaryHearing","thirdCommitteeHearing","thirdPlenaryHearing"));
		String status = "''";
		int countHearing = 0;
		for (String label : hearingLabels){
			String hearing = rowData.get(label);
			if (hearing != null && !hearing.trim().isEmpty()){
				status = "'"+statusLabel.get(countHearing) + " - " + hearing+"'";
			}
			countHearing++;
		}
		return status;
	}

	/**
	 * Defines whether a row gives information about parent draft (as opposed to child draft)
	 * @param rowData
	 * @return
	 */
	private static boolean isPrimaryDraft(Map<String, String> rowData){
		String value = rowData.get("primaryDraftLaw");
		return (value != null && value.trim().equalsIgnoreCase("*"));
	}

	/**
	 * Completes SQL queries, when inserting data in draftlaw_draftlawdiscussion table
	 * @param insertHearingQuery
	 * @param hearingDescription
	 * @param registrationNumber
	 * @param bw
	 * @param stage
	 * @throws IOException
	 */
	private static void writeHearingQuery(String insertHearingQuery, String hearingDescription, 
			String registrationNumber, BufferedWriter bw, int stage) throws IOException{
		if (hearingDescription != null && !hearingDescription.isEmpty()){

			String hearingValues = null;
			String hearingDate = getDateFromHearingDescription(hearingDescription);
			String hearingDateForQuery = null;
			if (hearingDate != null){
				hearingDateForQuery = "TO_DATE('"+hearingDate+"','DD.MM.YYYY')";
			}else{
				hearingDateForQuery = "TO_DATE('01.01.1970','DD.MM.YYYY')";
			}

			String place = null;
			if (stage % 2 == 0){
				place = "Committee";
			}else{
				place = "Plenary";
			}
			if (registrationNumber != null){
				hearingValues = ("((SELECT id FROM draftlaw_draftlaw WHERE bill_number = '" + registrationNumber.trim() +"'),"+hearingDateForQuery+","+Integer.toString(stage)+
						",'"+place+"','"+place+"','"+place+"','N');");
				
			}

			if (hearingValues != null){
				bw.write(insertHearingQuery + hearingValues+"\n");
			}
		}
	}

	/**
	 * Formatting data to get it ready to be injected in SQL query clauses.
	 * @param data
	 * @param isLimitedInSpace
	 * @return
	 */
	private static String parseDataForQuery(String data, boolean isLimitedInSpace){
		String result = "";
		if (data != null){
			// Some fields are limited to 255 characters
			if (isLimitedInSpace && data.length() >= 255){
				data = data.substring(0, 250) + "...";
			}
			data = data.replaceAll("'", "\"");
			result = "'"+data+"'";
		}else{
			result = "''";
		}
		return result;
	}

	/**
	 * Parsing spreadsheet cell to find date.
	 * @param hearing
	 * @return date String
	 */
	private static String getDateFromHearingDescription(String hearing){
		String date = null;
		if (hearing != null){
			if (hearing.contains(".201")){
				// the description contains a date to extract.
				String[] dateSplit = hearing.split("\\(");
				if (dateSplit != null && dateSplit.length >= 2){
					String[] dateSplit2 = dateSplit[dateSplit.length-1].split("\\)");
					if (dateSplit2 != null && dateSplit2.length >= 1){
						date = dateSplit2[0];
					}
				}

				if (date == null || (date != null && !date.contains(".201"))){
					// For some cell, dates are not surrounded by parenthesis. They are just at the end of the description.
					dateSplit = hearing.split(" ");
					if (dateSplit != null && dateSplit.length >= 1){
						date = dateSplit[dateSplit.length-1];
					}

				}
			}
		}
		return date; 
	}

	/**
	 * Merges 2 SQL files into a single new one.
	 * @param files
	 * @param mergedFile
	 */
	public static void mergeFiles(File[] files, File mergedFile) {

		FileWriter fstream = null;
		BufferedWriter out = null;
		try {
			fstream = new FileWriter(mergedFile, true);
			out = new BufferedWriter(fstream);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		for (File f : files) {
			System.out.println("merging: " + f.getName());
			FileInputStream fis;
			try {
				fis = new FileInputStream(f);
				BufferedReader in = new BufferedReader(new InputStreamReader(fis));

				String aLine;
				while ((aLine = in.readLine()) != null) {
					out.write(aLine);
					out.newLine();
				}

				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
