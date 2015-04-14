package ru.taximaxim.whatsapp.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import net.sumppen.whatsapi4j.EventManager;
import net.sumppen.whatsapi4j.MessageProcessor;
import net.sumppen.whatsapi4j.WhatsApi;
import net.sumppen.whatsapi4j.WhatsAppException;
import net.sumppen.whatsapi4j.example.ExampleEventManager;
import net.sumppen.whatsapi4j.example.ExampleMessageProcessor;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for regional direction
 * 
 * @author ags
 *
 */
public class RGClient {

	static Logger logger = LoggerFactory.getLogger(RGClient.class);
	static String identity = "RGClient";
	static String nickname = "RGClient API";
			
	/**
	 * Убедитесь, что у вас есть телефон с работающей сим картой, на этот номер телефона
	 * придет СМС с подтверждающим кодом.
	 * 
	 * На этот номер телефона не должен быть уже зарегистрирован WhatsApp аккаунт.
	 * 
	 * 1. Получите код активации телефона в WhatsApp
	 * 
	 * myapp -n 79172701185 -c
	 * 
	 * 2. После выполнения первого шага, на номер телефона указанный в перрвом шаге должна прийти СМС
	 * с кодом подтверждения. Для подтверждения выполните:
	 * 
	 * myapp -n 79172701185 -a 123456
	 * 
	 * Результатом выполнения будет следующая информация:
	 * 
	 * {
	 *   "cost": "33.00",
	 *   "currency": "RUB",
	 *   "expiration": 1458980440,
	 *   "kind": "free",
	 *   "login": "79172701185",
	 *   "price": "33,00 руб.",
	 *   "price_expiration": 1430300678,
	 *   "pw": "7BrMlxAP472SyuC5mkLiuM4mLAL=",
	 *   "status": "ok",
	 *   "type": "existing"
	 * }
	 * 
	 * нас должно интересовать поле pw, в примере выше это 7BrMlxAP472SyuC5mkLiuM4mLAL=
	 * 
	 *  3. Запускаем рассылку
	 *  
	 *  myapp -n 79172701185 -p 7BrMlxAP472SyuC5mkLiuM4mLAL= -s "Это моё сообщение" -f phones_file.txt
	 * 
	 * @param args
	 * @throws JSONException 
	 * @throws WhatsAppException 
	 * @throws NoSuchAlgorithmException 
	 * @throws IOException 
	 * @throws UnknownHostException 
	 */
	public static void main(String[] args) throws NoSuchAlgorithmException, WhatsAppException, JSONException, UnknownHostException, IOException {
		logger.info("Starting an application 'TaxiMaxim WathsApp sender'");
		logger.debug("Prepare the command line parser");
		
		Options options = new Options();
		
		options.addOption("n", true, "Phone number - required for all others options");
		options.addOption("c", false, "Create the confirmation code (Step 1)");
		options.addOption("a", true, "Register code (Step 2), parameter is a code from Step 1 sended via SMS on phone in Step 1");
		options.addOption("p", true, "Password for WatsApp sender step");
		options.addOption("s", true, "Message body from command line (required -p)");
		options.addOption("F", true, "Message body from file (required -p)");
		options.addOption("e", true, "Encoding message body file (required -F) by default - UTF-8");
		options.addOption("f", true, "File list of recipients (required -p) one recipient by line");
		options.addOption("i", true, "Identity, default: RGClient");
		options.addOption("u", true, "Nickname, default: RGClient API");
		options.addOption("h", false, "Help message");

		////////////
		// Парсинг аргументов командной строки
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args);
		} catch (ParseException e) {
			logger.error("Возникла непредвиденная ошибка при попытке анализа аргументов командной строки");
			logger.error(e.getLocalizedMessage());
			System.exit(1);
		}

		if (args.length == 0 || cmd.hasOption("h")) {
			logger.debug("Options not found or has option -h");

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("app", options );
			System.exit(1);
		}
		
		if (cmd.hasOption("i")) {
			identity = cmd.getOptionValue("i");
			logger.info("Set identity to: " + identity);
		}
		
		if (cmd.hasOption("u")) {
			nickname = cmd.getOptionValue("u");
			logger.info("Set nickname to: " + nickname);
		}
		
		// Номер телфефона обязателен для каждого параметра
		checkPhoneNumber(cmd);

		// Логика обработки аргументов командной строки
		if (cmd.hasOption("c")) {
			logger.debug("Create code");

			createCode(cmd);
			
			logger.debug("Code created");
		} else if (cmd.hasOption("a")) {
			logger.debug("Register code");
			
			registerPhone(cmd);
			
			logger.debug("Code registered");
		} else if (cmd.hasOption("s") || cmd.hasOption("F")) {
			logger.debug("Send message");
			
			sendMessages(cmd);
			
			logger.info("All messages was sended");
		}
		/////////////
		
		logger.info("Application exit");
	}

	static String readFile(String path, String encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		String s = new String(encoded, encoding);
		
		return s;
	}
	
	static String readFile2(String path, String encoding) {

		StringBuffer b = new StringBuffer();
		try {
			File fileDir = new File(path);

			BufferedReader in = 
					new BufferedReader(
							new InputStreamReader(
									new FileInputStream(fileDir), Charset.forName(encoding)));

			String str;

			while ((str = in.readLine()) != null) {
				b.append(str);
			}

			in.close();
		} catch (UnsupportedEncodingException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return b.toString();
	}
	
	private static void sendMessages(CommandLine cmd) throws FileNotFoundException, IOException, NoSuchAlgorithmException, WhatsAppException {
		
		int error = 0;
		String message = null;
		if (cmd.hasOption("s")) {
			message = cmd.getOptionValue("s");
		}
		else if (cmd.hasOption("F")) {
			String encoding = cmd.hasOption("e") ? cmd.getOptionValue("e") : "UTF-8";
			message = readFile2(cmd.getOptionValue("F"), encoding);

			logger.debug("Encoded message is: " + message);
		} 
		
		String file;
		String password;
		
		if (!cmd.hasOption("p")) {
			logger.error("Option '-p' (password) - required");
			error++;
		}
		
		if (!cmd.hasOption("f")) {
			logger.error("Option '-f' (phone list) - required");
			error++;
		}
		
		if (error > 0) {
			System.exit(1);
		}
		
		file = cmd.getOptionValue("f");
		password = cmd.getOptionValue("p");

		WhatsApi wa = getApi(cmd.getOptionValue("n"), password);
		
		String outPhoneNumber;
		try (
		    InputStream fis = new FileInputStream(file);
		    InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
		    BufferedReader br = new BufferedReader(isr)
		) 
		{
		    while ((outPhoneNumber = br.readLine()) != null) 
		    {
				logger.info("Sending to: " + outPhoneNumber);
				String res = wa.sendMessage(outPhoneNumber, message);
				logger.debug(res);
		    }
		}
	}

	private static void registerPhone(CommandLine cmd) throws NoSuchAlgorithmException, UnknownHostException, WhatsAppException, IOException, JSONException {
		String phoneNumber = cmd.getOptionValue("n");
		String code = cmd.getOptionValue("a");
		
		WhatsApi wa = getApi(phoneNumber, null);

		JSONObject res = wa.codeRegister(code);
		logger.debug(res.toString(2));
		logger.info("Password is: " + res.get("pw").toString());
	}

	private static void createCode(CommandLine cmd) throws WhatsAppException, JSONException, NoSuchAlgorithmException, UnknownHostException, IOException {
		String phoneNumber = cmd.getOptionValue("n");

		WhatsApi wa = getApi(phoneNumber, null);

		JSONObject resp = wa.codeRequest("sms", null, null);
		logger.info("Registration sent: " + resp.toString(2));
	}

	private static WhatsApi getApi(String phoneNumber, String password) throws NoSuchAlgorithmException, WhatsAppException, UnknownHostException, IOException {
		return getApi(phoneNumber, password, identity, nickname);
	}
	
	private static WhatsApi getApi(String phoneNumber, String password, String identity, String nickname) throws NoSuchAlgorithmException, WhatsAppException, UnknownHostException, IOException {

		WhatsApi wa = new WhatsApi(phoneNumber, identity, nickname);
		EventManager eventManager = new ExampleEventManager();
		wa.setEventManager(eventManager );
		MessageProcessor mp = new ExampleMessageProcessor();
		wa.setNewMessageBind(mp);
		if (! wa.connect()) {
			logger.error("Failed to connect to WhatsApp");
			System.exit(1);
		}
		if (password != null) {
			wa.loginWithPassword(password);
		}
		
		return wa;
	}
	
	private static void checkPhoneNumber(CommandLine cmd) {
		logger.debug("Check phone number");
		if (cmd.hasOption("n") == false || cmd.getOptionValue("n") == null) {
			logger.error("Phone number is required. See option -n. Exiting.");
			System.exit(1);
		}
	}
}
