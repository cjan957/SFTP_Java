
/**
 * Code is taken from Computer Networking: A Top-Down Approach Featuring 
 * the Internet, second edition, copyright 1996-2002 J.F Kurose and K.W. Ross, 
 * All Rights Reserved.
 **/

import java.io.*;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


class TCPServer {
	private Socket connectionSocket;
	private BufferedReader inFromClient;
	private DataOutputStream outToClient;
	
	private InputStream dataInFromClient;
	private OutputStream dataOutToClient;

	private boolean passwordValid;
	private boolean usernameValid;
	private boolean accountValid;

	private boolean loggedIn;

	private String transmissionType = "B"; // default binary

	private final String HOME_DIRECTORY = FileSystems.getDefault().getPath("storage").toString();
	private String currentDirectory = HOME_DIRECTORY;

	Users userDetail;
	Database db;

	// private static String accountFile = "";
	private enum ResponseCodes {
		SUCCESS, ERROR, LOGGEDIN, EMPTY
	}

	public static void main(String argv[]) throws IOException {
		TCPServer server = new TCPServer();
		server.start();
	}

	public void start() {
		String clientRequest;

		while (true) {
			if (!connectionSocket.isClosed()) {
				clientRequest = readMessageFromClient();
				boolean validRequest = validateClientRequest(clientRequest);

				if (validRequest) {
					performRequest(clientRequest);
				} else {
					sendMessageToClient("invalid command", ResponseCodes.ERROR);
				}
			} else {
				System.out.println("connection was closed");
				break;
			}

		}
	}

	public TCPServer() throws IOException {
		ServerSocket welcomeSocket = new ServerSocket(6789);

		connectionSocket = welcomeSocket.accept();

		inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
		outToClient = new DataOutputStream(connectionSocket.getOutputStream());

		dataInFromClient = connectionSocket.getInputStream();
		dataOutToClient = connectionSocket.getOutputStream();
		
		sendMessageToClient("cjan957 SFTP Service", ResponseCodes.SUCCESS);
	}

	private void performRequest(String clientRequest) {
		// TODO Auto-generated method stub
		String[] requestBreakdown = clientRequest.split(" ");
		String command = requestBreakdown[0];

		if (command.equals("user")) {
			userCommand(clientRequest);
		} else if (command.equals("pass")) {
			passCommand(clientRequest);
		} else if (command.equals("acct")) {
			acctCommand(clientRequest);
		} else if (command.equals("type")) {
			typeCommand(clientRequest);
		} else if (command.equals("list")) {
			listCommand(clientRequest);
		} else if (command.equals("cdir")) {
			cdirCommand(clientRequest);
		} else if (command.equals("kill")) {
			killCommand(clientRequest);
		} else if (command.equals("name")) {
			nameCommand(clientRequest);
		} else if (command.equals("done")) {
			doneCommand();
		} else if (command.equals("retr")) {
			retrCommand(clientRequest);
		} else if (command.equals("stor")) {
			storCommand(clientRequest);
		}
		else {
			sendMessageToClient("invalid input", ResponseCodes.ERROR);
		}

	}

	private void storCommand(String clientRequest) {
		String[] requestBreakdown = clientRequest.split(" ");
		
		String type = requestBreakdown[1].toUpperCase();
		String fileName = clientRequest.substring(9); // STOR XXX {FILENAME} (9th char)
		
		if(type.equals("NEW") || type.equals("OLD") || type.equals("APP"))
		{
			File file = new File(currentDirectory + "/" + fileName);

			switch (type)
			{
			case "NEW":
				//Assume doesn't support file generation
				if(file.exists())
				{
					sendMessageToClient("File exists, will create new generation of file", ResponseCodes.SUCCESS);		
					waitForFileSizeFromClient(fileName,file, 2);
				}
				else
				{
					//+File does not exist, will create new file
					sendMessageToClient("File does not exist, will create new file", ResponseCodes.SUCCESS);
					waitForFileSizeFromClient(fileName, file, 1);

				}
				
				break;
			case "OLD":
				//overwrite or create a new one
				if(file.exists())
				{
					//Will write over old file
					sendMessageToClient("Will write over old file", ResponseCodes.SUCCESS);
					waitForFileSizeFromClient(fileName, file, 3);

					
				}
				else
				{
					//will create new file
					sendMessageToClient("Will create new file", ResponseCodes.SUCCESS);
					waitForFileSizeFromClient(fileName, file, 1);

				}
				
				break;
			case "APP":
				//append or create
				if(file.exists())
				{
					sendMessageToClient("Will append to file", ResponseCodes.SUCCESS);
					waitForFileSizeFromClient(fileName, file, 4);

				}
				else
				{
					sendMessageToClient("Will create file", ResponseCodes.SUCCESS);
					waitForFileSizeFromClient(fileName, file, 1);

				}
				break;
			default:
				sendMessageToClient("Invalid type, STOR aborted", ResponseCodes.ERROR);
				break;
					
			}			
		}
	}

	
	//Operation: 1 = create a new file, 2 = NEW generation 3 = Overwrite, 4 = Append
	private void waitForFileSizeFromClient(String fileName, File file, int operation) {
		String clientResponse = readMessageFromClient();
		
		//Response should be: "SIZE ######"
		String[] requestBreakdown = clientResponse.split(" ");

		if(requestBreakdown[0].toUpperCase().equals("SIZE"))
		{
			long fileSize = 0;
			try {
				fileSize = Long.parseLong(requestBreakdown[1]);
			}
			catch (NumberFormatException e){
				//invalid file size! TODO:
				return;
			}
		
			File localPath = new File(currentDirectory);
			long availableSpace = localPath.getUsableSpace();
			
			if(fileSize < availableSpace)
			{
				sendMessageToClient("ok, waiting for file", ResponseCodes.SUCCESS);

				//create a new generation
				if(operation == 2)
				{
					List<String> dotBreakdown = new LinkedList<String>(Arrays.asList(fileName.split("\\.")));
					
					if(dotBreakdown.size() >= 2)
					{
						String fileExtension = dotBreakdown.get(dotBreakdown.size() - 1);
						dotBreakdown.remove(dotBreakdown.size() - 1);
						String fileNameNoExt = String.join(".", dotBreakdown);					
						
						int generation = 1;
						while(true)
						{
							file = new File(currentDirectory + "/" + fileNameNoExt + "(" + generation + ")." + fileExtension);
							if(!file.exists())
							{
								break;
							}
							generation++;
						}
						fileName = fileNameNoExt + "(" + generation + ")." + fileExtension;
					}
				}
				
				
				//do file operations
				FileOutputStream dataWriteLocal;
				try {
					
					if(operation == 4) {
						dataWriteLocal = new FileOutputStream(currentDirectory + "/" + fileName, true);
					}
					else {
						dataWriteLocal = new FileOutputStream(currentDirectory + "/" + fileName);
					}
					
					
					if(transmissionType.equals("A"))
					{
						Reader r = new InputStreamReader(dataInFromClient, "US-ASCII");
						//Writer w = new OutputStreamWriter(dataWriteLocal, "US-ASCII");
						
						int buffer;
						int totalCount = 0;
						
						while((buffer = r.read()) > 0)
						{
							char ch = (char) buffer;
							dataWriteLocal.write(ch);
							totalCount += 1;
							if(totalCount == fileSize) break;
						}
					}
					
					else 
					{
						byte[] bytes = new byte[1];
						int count;
						int totalCount = 0;

						while((count = dataInFromClient.read(bytes)) > 0)
						{
							dataWriteLocal.write(bytes, 0, count);
							totalCount += count;
							if(totalCount == fileSize) break;
						}
						
						System.out.println("Total read: " + totalCount);
						
						dataWriteLocal.close();
					}
					
					
				} catch (IOException e1) {
					e1.printStackTrace();
					return;
				}
								
				sendMessageToClient("Saved " + fileName, ResponseCodes.SUCCESS);
				
			}
			else
			{
				sendMessageToClient("Not enough room, don't send it", ResponseCodes.ERROR);
			}
		}
		else
		{
			sendMessageToClient("Invalid message received, expected SIZE ####", ResponseCodes.ERROR);
			System.out.println("messagefromclient: " + clientResponse);
		}
	}

	private void retrCommand(String clientRequest) {
		// TODO Auto-generated method stub
		String[] requestBreakdown = clientRequest.split(" ");
		
		if(requestBreakdown.length > 1)
		{
			String argument = clientRequest.substring(clientRequest.indexOf(' ') + 1);
				
			File file = new File(currentDirectory + "/" + argument);
			long size;
				
			if(!file.isFile() || !file.exists())
			{
				sendMessageToClient("File doesn't exist", ResponseCodes.ERROR);
				return;
			}
			
			try {
				BasicFileAttributes basic_attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
				size = basic_attr.size();
				
				sendMessageToClient(Long.toString(size), ResponseCodes.EMPTY);
			}
			catch(Exception e)
			{
				sendMessageToClient("File doesn't exist", ResponseCodes.ERROR);
				return;
			}
			
			if(waitClientForSEND())
			{
				System.out.println("System received SEND");
				try {
					sendFile(file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
			
	}
	private void sendFile(File file) throws IOException {		
		if(transmissionType.equals("A"))
		{			
			int buffer;
			FileInputStream in = new FileInputStream(file);
			Writer w = new OutputStreamWriter(dataOutToClient, "US-ASCII");
			
			while((buffer = in.read()) > 0)
			{
				char ch = (char) buffer;
				w.write(ch);
			}
			System.out.println("Server sent A!");	
			//r.close();
			w.flush();
			//dataOutToClient.flush();
		}
		//B and C modes are the same.
		else
		{
			byte buffer[] = new byte[1];
			FileInputStream in = new FileInputStream(file);
			
			while((in.read(buffer)) > 0)
			{
				dataOutToClient.write(buffer);
			}
			
			System.out.println("Server sent B/C!");
			//in.close();
			dataOutToClient.flush();
		}
	}

	private boolean waitClientForSEND() {
		while (true) {
			String clientRequest = readMessageFromClient();
			if(clientRequest.toUpperCase().equals("SEND"))
			{
				return true;
			}
			else if(clientRequest.toUpperCase().equals("STOP"))
			{
				sendMessageToClient("ok, RETR aborted", ResponseCodes.ERROR);
				return false;
			}
			else
			{
				sendMessageToClient("invalid command, RETR aborted", ResponseCodes.ERROR);
				return false;
			}
		}
		
	}

	private void nameCommand(String clientRequest) {
		String[] requestBreakdown = clientRequest.split(" ");

		if (requestBreakdown.length > 1) {
			String argument = clientRequest.substring(clientRequest.indexOf(' ') + 1);

			File file = new File(currentDirectory + "/" + argument);

			if (file.isFile()) {
				sendMessageToClient("File exists", ResponseCodes.SUCCESS);
			} else {
				sendMessageToClient("Can't find " + argument, ResponseCodes.ERROR);
				return;
			}

			// Blocking read
			String newName = tobeCommand();

			if (newName != null) {
				File newFile = new File(currentDirectory + "/" + newName);

				if (newFile.exists()) {
					sendMessageToClient(
							"File wasn't renamed because this name is already taken. Try again with a different name",
							ResponseCodes.ERROR);
					return;
				}

				if (file.renameTo(newFile)) {
					sendMessageToClient(argument + " renamed to " + newName, ResponseCodes.SUCCESS);
				} else {
					sendMessageToClient("File wasn't renamed because it was failed to rename. Try Linux",
							ResponseCodes.ERROR);
					return;
				}
			}
			else {
				sendMessageToClient("File wasn't renamed because of an invalid command or a missing argument",
						ResponseCodes.ERROR);
				return;
			}
		} else {
			sendMessageToClient("Missing argument", ResponseCodes.ERROR);
		}
	}

	private String tobeCommand() {
		while (true) {
			String clientRequest = readMessageFromClient();

			String[] requestBreakdown = clientRequest.split(" ");

			// Check if there's at least one space in the command i.e. file name is provided
			if (requestBreakdown.length > 1) {
				// then split the first word (command) out of the rest (which may include
				// multiple spaces)
				// i.e. TOBE new File.jpg --> [TOBE] [new File.jpg]
				String command = clientRequest.substring(0, clientRequest.indexOf(' '));
				String argument = clientRequest.substring(clientRequest.indexOf(' ') + 1);

				if (command.toUpperCase().equals("TOBE")) {
					return argument;
				} else {
					return null;
				}

			} else {
				return null;
			}
		}
	}

	private void killCommand(String clientRequest) {
		String argument = clientRequest.substring(clientRequest.indexOf(' ') + 1);

		File file = new File(currentDirectory + "/" + argument);

		if (file.isFile()) {
			if (file.delete()) {
				sendMessageToClient(file.getName() + " deleted", ResponseCodes.SUCCESS);
			} else {
				sendMessageToClient("Not delete because failed to delete / folder delete is not supported",
						ResponseCodes.ERROR);
			}
		} else {
			sendMessageToClient("Not deleted because not a file", ResponseCodes.ERROR);
		}
	}

	private void cdirCommand(String clientRequest) {
		// TODO Auto-generated method stub
		String[] requestBreakdown = clientRequest.split(" ");

		if (requestBreakdown.length == 2) {
			// Default + /new/directory
			File filePath = new File(HOME_DIRECTORY + requestBreakdown[1]);
			if (!filePath.isDirectory()) {
				sendMessageToClient("Can't connect to directory because: invalid directory", ResponseCodes.ERROR);
				return;
			}

			if (!passwordValid || !accountValid) {
				sendMessageToClient("Directory ok, send account/password", ResponseCodes.SUCCESS);

				// The server will wait for ACCT or PASS
				while (!authenticateCDIR()) {
				}
			}

			currentDirectory = filePath.getPath();
			sendMessageToClient("Changed working dir to " + currentDirectory, ResponseCodes.LOGGEDIN);

		} else {
			sendMessageToClient("Can't connect to directory because: invalid arguments", ResponseCodes.ERROR);
			return;
		}
	}

	private boolean authenticateCDIR() {
		// TODO Auto-generated method stub
		while (true) {
			String clientRequest = readMessageFromClient();
			boolean validRequest = validatePassAcctRequest(clientRequest);

			if (validRequest) {
				String command = clientRequest.substring(0, clientRequest.indexOf(' '));
				String argument = clientRequest.substring(clientRequest.indexOf(' ') + 1);

				command = command.toUpperCase();
				boolean foundAccount = false;

				switch (command) {
				case "ACCT":
					if (userDetail.hasAccounts()) {
						String[] accountArray = userDetail.getAccounts();
						for (int i = 0; i < accountArray.length; i++) {
							if (accountArray[i].equals(argument)) {
								// Account found
								foundAccount = true;
								break;
							}
						}

						if (foundAccount) {
							accountValid = true;

							// everything is checked, has no password
							if (passwordValid || !userDetail.hasPassword()) {
								logIn();
								return true;
							}

							// has password, not yet validated
							else if (!passwordValid && userDetail.hasPassword()) {
								sendMessageToClient("account ok, send password", ResponseCodes.SUCCESS);
							}
						} else {
							sendMessageToClient("Invalid account", ResponseCodes.ERROR);
						}
					} else {
						accountValid = true;
						// has no account, password already valid OR has no account, has no password
						if (passwordValid || !userDetail.hasPassword()) {
							logIn();
							return true;
						}

						// has no account, but password required
						else if (!passwordValid && userDetail.hasPassword()) {
							sendMessageToClient("account ok, send password", ResponseCodes.SUCCESS);
						}
					}
					break;
				case "PASS":
					if (userDetail.hasPassword()) {
						// password valid
						if (userDetail.getPassword().equals(argument)) {
							passwordValid = true;
							if (!userDetail.hasAccounts() || accountValid) {
								logIn();
								return true;
							} else {
								sendMessageToClient("password ok, send account", ResponseCodes.SUCCESS);
							}
						} else {
							sendMessageToClient("invalid password", ResponseCodes.ERROR);
						}
					} else {
						passwordValid = true;
						// doesn't have an account or already authorised
						if (!userDetail.hasAccounts() || accountValid) {
							logIn();
							return true;
						} else {
							sendMessageToClient("password ok, send account", ResponseCodes.SUCCESS);
						}
					}
					break;
				}
			} else {
				sendMessageToClient("invalid command", ResponseCodes.ERROR);
			}
		}
	}

	private void listCommand(String clientRequest) {
		String[] requestBreakdown = clientRequest.split(" ");
		String mode = "";
		String listOfFile_out = "";

		// LIST + F or V, current directory!
		if (requestBreakdown.length == 2 || requestBreakdown.length == 3) {
			File filePath = new File(currentDirectory);

			if (requestBreakdown.length == 3) {
				filePath = new File(currentDirectory + "/" + requestBreakdown[2]);
				if (!filePath.isDirectory()) {
					sendMessageToClient("Invalid directory", ResponseCodes.ERROR);
					return;
				}
			}

			mode = requestBreakdown[1].toUpperCase();

			if (mode.equals("F") || mode.equals("V")) {
				File[] fileList = filePath.listFiles();

				listOfFile_out = listOfFile_out.concat(String.format("%s\r\n", currentDirectory));

				for (int i = 0; i < fileList.length; i++) {
					String fileName = fileList[i].getName();

					if (fileList[i].isFile()) {
						if (mode.equals("F")) {
							listOfFile_out = listOfFile_out.concat(String.format("%s\r\n", fileName));
						} else {
							try {
								BasicFileAttributes basic_attr = Files.readAttributes(fileList[i].toPath(),
										BasicFileAttributes.class);
								FileOwnerAttributeView owner_attr = Files.getFileAttributeView(fileList[i].toPath(),
										FileOwnerAttributeView.class);

								long size = basic_attr.size();

								FileTime lastModified = basic_attr.lastModifiedTime();

								SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");

								String dateModified = format.format(lastModified.toMillis());

								String owner = owner_attr.getOwner().getName();

								String fileInfo = String.format("%s %s %s %s", dateModified, owner, size,  fileName);

								listOfFile_out = listOfFile_out.concat(String.format("%s\r\n", fileInfo));
							} catch (Exception e) {
								sendMessageToClient("Files access errors", ResponseCodes.ERROR);
								break;
							}
						}
					} else {
						// if folder
						String folderName = String.format("%s/\r\n", fileName);
						listOfFile_out = listOfFile_out.concat(folderName);
					}
				}

				if (mode.equals("F")) {
					// listing is terminated with <NULL> after the last <CRLF> (loop)
					listOfFile_out.concat(Character.toString('\0'));
				}
				sendMessageToClient(listOfFile_out, ResponseCodes.SUCCESS);
			} else {
				sendMessageToClient("Invalid Mode", ResponseCodes.ERROR);
				return;
			}

		} else {
			sendMessageToClient("Invalid arguments", ResponseCodes.ERROR);
			return;
		}

	}

	private void doneCommand() {
		sendMessageToClient("Charge/Accounting info", ResponseCodes.SUCCESS);
		try {
			connectionSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void typeCommand(String clientRequest) {
		if (!loggedIn) {
			sendMessageToClient("Not logged in", ResponseCodes.ERROR);
			return;
		}

		String[] requestBreakdown = clientRequest.split(" ");
		if (hasArgument(requestBreakdown) && requestBreakdown.length == 2) {
			switch (requestBreakdown[1].toUpperCase()) {
			case "A":
				transmissionType = "A";
				sendMessageToClient("Using Ascii mode", ResponseCodes.SUCCESS);
				break;
			case "B":
				transmissionType = "B";
				sendMessageToClient("Using Binary mode", ResponseCodes.SUCCESS);
				break;
			case "C":
				transmissionType = "C";
				sendMessageToClient("Using Continuous mode", ResponseCodes.SUCCESS);
				break;
			default:
				sendMessageToClient("Type not valid", ResponseCodes.ERROR);
			}
		} else {
			sendMessageToClient("Type not valid", ResponseCodes.ERROR);
		}

	}

	private boolean hasArgument(String[] message) {
		if (message.length > 1) {
			return true;
		} else {
			return false;
		}
	}

	private void userCommand(String clientRequest) {

		// breakdown the request using space
		String[] requestBreakdown = clientRequest.split(" ");

		// i.e. USER<SPACE>cjan957, assumes username has no space
		if (requestBreakdown.length == 2) {
			String username = requestBreakdown[1];

			db = new Database();
			db.connect();

			userDetail = db.searchForUser(username);

			// username found in db
			if (userDetail.getValid() == 1) {
				usernameValid = true;
				passwordValid = false;
				accountValid = false;
				// has a password
				if (!userDetail.hasPassword()) {
					passwordValid = true;
					if (!userDetail.hasAccounts()) {
						accountValid = true;
						sendMessageToClient(username + " logged in", ResponseCodes.LOGGEDIN);
						logIn();
					}
					else
					{
						sendMessageToClient("User-id valid, send account and password", ResponseCodes.SUCCESS);
					}
				} else {
					sendMessageToClient("User-id valid, send account and password", ResponseCodes.SUCCESS);
					loggedIn = false;
				}
			} else {
				usernameValid = false;
				sendMessageToClient("Invalid user-id, try again", ResponseCodes.ERROR);
			}
		}
		// i.e. USER
		else {
			sendMessageToClient("Invalid user-id, try again", ResponseCodes.ERROR);
		}
	}

	private void passCommand(String clientRequest) {
		// split the PASS command from the actual password
		String command = clientRequest.substring(0, clientRequest.indexOf(' '));
		String password = clientRequest.substring(clientRequest.indexOf(' ') + 1);

		if (usernameValid) {
			if (userDetail.hasPassword()) {
				if (userDetail.getPassword().equals(password)) {
					passwordValid = true;
					if (!userDetail.hasAccounts() || accountValid) {
						accountValid = true;
						sendMessageToClient("Logged in", ResponseCodes.LOGGEDIN);
						logIn();
					} else {
						sendMessageToClient("Send Account", ResponseCodes.SUCCESS);
					}
				} else {
					sendMessageToClient("Wrong password, try again", ResponseCodes.ERROR);
				}
			} else {
				// force passwordValid to true even though there's no password
				passwordValid = true;
				if (!userDetail.hasAccounts() || accountValid) {
					accountValid = true;
					sendMessageToClient("Logged in", ResponseCodes.LOGGEDIN);
					logIn();
				} else {
					sendMessageToClient("Send Account", ResponseCodes.SUCCESS);
				}
			}

		}

	}

	private void acctCommand(String clientRequest) {
		// ACCT accountName
		String[] requestBreakdown = clientRequest.split(" ");
		String accountName = requestBreakdown[1];
		boolean foundAccount = false;

		if (usernameValid) {
			// ACCT <cjan957>
			if (requestBreakdown.length == 2) {
				// user from db has an account
				if (userDetail.hasAccounts()) {
					String[] accountArray = userDetail.getAccounts();
					for (int i = 0; i < accountArray.length; i++) {
						if (accountArray[i].equals(accountName)) {
							// Account found
							foundAccount = true;
							break;
						}
					}

					// account specified by client matches db
					if (foundAccount) {
						accountValid = true;
						if (passwordValid) {
							sendMessageToClient("Account valid, logged-in", ResponseCodes.LOGGEDIN);
							logIn();
						} else if (!passwordValid && userDetail.hasPassword()) {
							sendMessageToClient("Account valid, send password", ResponseCodes.SUCCESS);
						} else if (!userDetail.hasPassword()) {
							sendMessageToClient("Account valid, logged-in", ResponseCodes.LOGGEDIN);
							logIn();
						}
					} else {
						sendMessageToClient("Invalid account, try again", ResponseCodes.ERROR);
					}
				}
				// if user has no account in remote / db
				else {
					// force accountValid to true, even if they have no account
					accountValid = true;
					if (passwordValid) {
						sendMessageToClient("Account valid, logged-in", ResponseCodes.LOGGEDIN);
						logIn();
					} else if (!passwordValid && userDetail.hasPassword()) {
						sendMessageToClient("Account valid, send password", ResponseCodes.SUCCESS);
					} else if (!userDetail.hasPassword()) {
						sendMessageToClient("Account valid, logged-in", ResponseCodes.LOGGEDIN);
						logIn();

					}
				}
			}
		}
	}

	private void logIn() {
		loggedIn = true;
	}

	private boolean isLoggedIn() {
		return loggedIn;
	}

	private String readMessageFromClient() {
		String requestBuffer = "";
		int character = 0;

		while (true) {
			try {
				character = inFromClient.read();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// Check for null
			if (character == 0) {
				break;
			}
			String charBychar = Character.toString((char) character);
			requestBuffer = requestBuffer.concat(charBychar);
		}
		return requestBuffer;
	}

	private void sendMessageToClient(String message, ResponseCodes status) {
		String statusSymbol = "";
		switch (status) {
		case SUCCESS:
			statusSymbol = "+";
			break;
		case ERROR:
			statusSymbol = "-";
			break;
		case LOGGEDIN:
			statusSymbol = "!";
			break;
		case EMPTY:
			statusSymbol = " ";
			break;
		}
		try {
			statusSymbol = statusSymbol.concat(message).concat(Character.toString('\0'));
			outToClient.writeBytes(statusSymbol);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private boolean validateClientRequest(String request) {

		// split request to check the command
		String[] requestBreakdown = request.split(" ");
		
		System.out.println("comamnd received in validateClientCommand : " + request);

		String command = requestBreakdown[0];

		if (command.length() != 4) {
			return false;
		}

		// 4 ASCII command should be of any case.
		String lowerCommand = command.toLowerCase();

		if (lowerCommand.equals("user") || lowerCommand.equals("acct") || lowerCommand.equals("pass")
				|| lowerCommand.equals("type") || lowerCommand.equals("list") || lowerCommand.equals("cdir")
				|| lowerCommand.equals("kill") || lowerCommand.equals("name") || lowerCommand.equals("done")
				|| lowerCommand.equals("retr") || lowerCommand.equals("stor")) {
			System.out.println("valid request");
			return true;
		}
		System.out.println("invalid request");
		return false;
	}

	private boolean validatePassAcctRequest(String request) {

		// split request to check the command
		String[] requestBreakdown = request.split(" ");

		String command = requestBreakdown[0];

		if (command.length() != 4) {
			return false;
		}

		// 4 ASCII command should be of any case.
		String lowerCommand = command.toLowerCase();

		if (lowerCommand.equals("acct") || lowerCommand.equals("pass")) {
			System.out.println("valid pass act request");
			return true;
		}
		System.out.println("invalid pass act request");
		return false;
	}

}
