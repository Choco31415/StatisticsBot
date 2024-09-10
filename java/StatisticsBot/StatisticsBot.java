package StatisticsBot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.logging.Level;

import Content.Page;
import Content.PageLocation;
import Content.Section;
import Content.User;
import Content.SiteInfo.SiteStatistics;
import Mediawiki.MediawikiDataManager;
import Utils.FileUtils;
import WikiBot.MediawikiBot;
import APIcommands.APIcommand;
import APIcommands.EditPage;

public class StatisticsBot {
	
	private static final long serialVersionUID = 1L;

	MediawikiBot bot;
	
	private static String username;
	private static String password;
	private static String language;
	private static User botUser;
	private static String botPropFile;
	
	private static StatisticsBot instance;
	
	private static PageLocation statsPage;
	private static String defaultStatsFile;
	
	private static String[] statsTracked;// Which statistics are we tracking?
	
	private static SimpleDateFormat dateFormat;
	
	/*
	 * This is where I initialize my custom Mediawiki bot.
	 */
	public StatisticsBot() throws IOException {
		Path familyFile = Paths.get("./src/main/resources/ScratchFamily.txt");
		
		bot = new MediawikiBot(familyFile, "en");
		
		//Preferences
		bot.queryLimit = 30;//The amount of items to get per query call, if there are multiple items.
		
		bot.APIdelay = 0.5;//Minimum time between any API commands.
		
		botPropFile = "/BotProperties.properties";
		
		statsPage = new PageLocation(language, "User:" + username + "/International Stats");
		defaultStatsFile = "/DefaultStatsPage.txt";
		
		statsTracked = new String[]{"pages", "articles", "edits", "images", "users", "activeusers", "admins"};
		
		dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
		
		if (instance == null) {
			instance = this;
		} else {
			throw new ConcurrentModificationException();//There should not be more then one GenericBot!!!
		}
	}
	
	/**
	 * Get an instance of GenericBot.
	 * If GenericBot has not been instantiated yet, the
	 * family and homeWikiLanguage are both set to null.
	 * @return
	 * @throws IOException 
	 */
	public static StatisticsBot getInstance() throws IOException {
		if (instance == null) {
			instance = new StatisticsBot();
		}
		
		return instance;
	}
	
	/*
	 * This is where I read in the bot password and create an instance.
	 */
	public static void main(String[] args) throws IOException {
		getInstance().run(); // TODO
	}
	
	/**
	 * This is the entry point for the Object portion of the bot.
	 * 
	 * @param botPassword The password of the bot.
	 * @param delay The amount of seconds to delay checks.
	 */
	public void run() {
		loadPropFile();
		statsPage = new PageLocation(language, "User:" + username + "/International Stats");
		
		boolean loggedIn = bot.logIn(botUser, password);
		
		if (!loggedIn) {
			throw new Error("Didn't log in.");
		}
		
		runChecks();
	}
	
	
	public void loadPropFile() {
		ArrayList<String> properties = new ArrayList<>();
		properties.add("username");
		properties.add("password");
		properties.add("language");
		
		ArrayList<String> values = FileUtils.readProperties(botPropFile, properties);
		
		username = values.get(0);
		password = values.get(1);
		language = values.get(2);
		botUser = new User(language, username);
	}

	/*
	 * This is the method where StatisticsBot collects statistics on all wikis, and then
	 * pushes them to the wiki.
	 */
	public void runChecks() {
		// Check if the statistics page has been initialized...
		Page sp = null;
		boolean pageExists = bot.doesPageExist(statsPage);
		if (pageExists) {
			sp = bot.getWikiPage(statsPage, false);
		}
		
		if (!pageExists || !sp.getRawText().contains("<!--Initialized-->")) {
			// Not initialized, so initialize it.
			String defaultText = generateDefaultPageText();
			
			APIcommand initPage = new EditPage(statsPage, defaultText, "Initializing.");
			bot.APIcommand(initPage); // Push text.
			
			sp = bot.getWikiPage(statsPage, false);
		}
		
		String rawText = sp.getRawText();
		
		/*
		 * Now for the statistics collection.
		 */
		
		// Gather wiki data...
		for (int sectionID = sp.getNumSections(); sectionID > 0; sectionID--) {
			Section section = sp.getSectionByNum(sectionID);
			String sectionTitle = section.getSectionTitle().trim().toLowerCase();
			
			String timeStamp = dateFormat.format(new java.util.Date());
			
			System.out.println(sectionTitle);
			
			if (bot.getMDM().getWikiPrefixes().contains(sectionTitle)) {
				String wikiPrefix = sectionTitle;
				
				// Parse out section information.
				int startingPos = section.getPosition();
				int endingPos = -1;
				if (sectionID == sp.getNumSections()) {
					endingPos = rawText.length();
				} else {
					Section nextSection = sp.getSectionByNum(sectionID+1);
					endingPos = nextSection.getPosition();
				}
				
				String sectionText = rawText.substring(startingPos, endingPos);
				
				// Get wiki statistics...
				SiteStatistics ss = bot.getSiteStatistics(wikiPrefix);
				
				// Format it into wiki text.
				String insertText = "|-";
				
				// Time...
				insertText += "\n|" + timeStamp;
						
				// Stats...
				for (String stat : statsTracked) {
					insertText += "\n|";
					if (ss.hasProperty(stat)) {
						insertText += ss.getValue(stat);
					}
				}
				
				insertText += "\n";
				
				// Insert the text.
				int pos = startingPos + sectionText.indexOf("|}");
				
				rawText = rawText.substring(0, pos) + insertText + rawText.substring(pos);
			}
		}
		
		// Update the page.
		APIcommand updatePage = new EditPage(statsPage, rawText, "Weekly update.");
		bot.APIcommand(updatePage);
	}
	
	public String generateDefaultPageText() {
		String defaultText = FileUtils.readFile(defaultStatsFile);
		
		MediawikiDataManager mdm = bot.getMDM();
		ArrayList<String> wikiPrefixes = mdm.getWikiPrefixes();
		Collections.sort(wikiPrefixes);
		
		for (String wikiPrefix : wikiPrefixes) {
			String apiURL = mdm.getWikiURL(wikiPrefix);
			String siteURL = apiURL.substring(0, apiURL.lastIndexOf('/')) + "/wiki";
			String allArticlesURL = siteURL + "/Special:AllPages";
			String recentChangesURL = siteURL + "/Special:RecentChanges";
			String allImagesURL = apiURL + "/index.php?title=Special:AllPages&namespace=6";
			String allUsersURL = siteURL + "/Special:ListUsers";
			String activeUsersURL = siteURL + "/Special:ActiveUsers";
			String allAdminsURL = apiURL + "/index.php?title=Special:ListUsers&group=sysop";
			
			defaultText += "\n== " + wikiPrefix + " ==\n\n";
			
			defaultText += "{|class='wikitable sortable' style='width:700px'"
			+ "\n|-"
			+ "\n!Time"
			+ "\n!Pages"
			+ "\n![" + allArticlesURL + " Articles]"
			+ "\n![" + recentChangesURL + " Edits]"
			+ "\n![" + allImagesURL + " Images]"
			+ "\n![" + allUsersURL + " Users]"
			+ "\n![" + activeUsersURL + " Active Users]"
			+ "\n![" + allAdminsURL + " Admins]"
			+ "\n|}\n";
		}
		
		return defaultText;
	}
}