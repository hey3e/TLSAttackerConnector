package nl.cypherpunk.tlsattackerconnector;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Security;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.bouncycastle.crypto.tls.AlertDescription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import de.rub.nds.modifiablevariable.bytearray.ByteArrayModificationFactory;
import de.rub.nds.modifiablevariable.bytearray.ModifiableByteArray;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.AlertLevel;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CompressionMethod;
import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.message.AlertMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ApplicationMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.DHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.DHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ECDHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.protocol.message.RSAClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloDoneMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
import de.rub.nds.tlsattacker.transport.ClientConnectionEnd;
import de.rub.nds.tlsattacker.transport.TransportHandler;
import de.rub.nds.tlsattacker.transport.TransportHandlerFactory;
import de.rub.nds.tlsattacker.util.UnlimitedStrengthEnabler;

/**
 * @author Joeri de Ruiter (joeri@cs.ru.nl)
 *
 */
public class TLSAttackerConnector {
	static String SYMBOL_CONNECTION_CLOSED = "ConnectionClosed";
	static String SYMBOL_RESET = "RESET";
	
	Config config;
	State state;

	@Parameter(names = {"--listen", "-l"}, description = "Listen port")
	int listenPort = 6666;	
	@Parameter(names = {"--targetHost", "-tH"}, description = "Target host")
	String targetHostname = "localhost";
	@Parameter(names = {"--targetPort", "-tP"}, description = "Target port")
	int targetPort = 4433;
	@Parameter(names = {"--timeout", "-t"}, description = "Timeout")
	int timeout = 100;
	
	@Parameter(names = {"--cipherSuite", "-cS"}, description = "CipherSuite to use")
	String cipherSuiteString = "TLS_RSA_WITH_AES_128_CBC_SHA256";
	@Parameter(names = {"--protocolVersion", "-pV"}, description = "TLS version to use")
	String protocolVersionString = "TLS12";
	@Parameter(names = {"--compressionMethod", "-cM"}, description = "CompressionMethod to use")
	String compressionMethodString = "NULL";
	
	@Parameter(names = {"--help", "-h"}, description = "Display help", help = true)
	private boolean help;
	@Parameter(names = {"--test"}, description = "Run test handshake")
	private boolean test;
	
	/**
	 * Create the TLS-Attacker connector
	 * 
	 */
	public TLSAttackerConnector() {
		// Add BouncyCastle, otherwise encryption will be invalid and it's not possible to perform a valid handshake
		Security.addProvider(new BouncyCastleProvider());
		UnlimitedStrengthEnabler.enable();
		
		// Disable logging
		Configurator.setAllLevels("de.rub.nds.tlsattacker", Level.OFF);
	}
	
	/**
	 * Intialise the TLS-Attacker connector
	 * 
	 * @throws Exception
	 */	
	public void initialise() throws Exception {
		// Configure TLS-Attacker
		config = Config.createConfig();
		config.setEnforceSettings(false);
		
		// Configure hosts
		config.clearConnectionEnds();
		ClientConnectionEnd connectionEnd = new ClientConnectionEnd();
		connectionEnd.setHostname(targetHostname);
		connectionEnd.setPort(targetPort);
		config.addConnectionEnd(connectionEnd);
		
		// Timeout that is used when waiting for incoming messages
		config.setDefaultTimeout(timeout);		
		
		// Parse provided CipherSuite
		CipherSuite cipherSuite;
		try {
			cipherSuite = CipherSuite.valueOf(cipherSuiteString);
		}
		catch(java.lang.IllegalArgumentException e) {
			throw new Exception("Unknown CipherSuite " + cipherSuiteString); 
		}
		
		// Parse CompressionMethod
		CompressionMethod compressionMethod;
		try {
			compressionMethod = CompressionMethod.valueOf(compressionMethodString);
		}
		catch(java.lang.IllegalArgumentException e) {
			throw new Exception("Unknown CompressionMethod " + compressionMethodString); 
		}				
		
		// TLS specific settings
		
		// Set TLS version
		ProtocolVersion protocolVersion = ProtocolVersion.fromString(protocolVersionString);
		config.setHighestProtocolVersion(protocolVersion);
		config.setDefaultSelectedProtocolVersion(protocolVersion);
		config.setDefaultHighestClientProtocolVersion(protocolVersion);
		
		// Set supported CipherSuite
		config.setDefaultSelectedCipherSuite(cipherSuite);
		
		// Create the list of supported cipher suites
		List<CipherSuite> cipherSuites = new LinkedList<>();
		cipherSuites.add(config.getDefaultSelectedCipherSuite());
		config.setDefaultClientSupportedCiphersuites(cipherSuites);
		
		// Set supported compression algorithms
		List<CompressionMethod> compressionMethods = new LinkedList<>();	
		compressionMethods.add(compressionMethod);
		config.setDefaultClientSupportedCompressionMethods(compressionMethods);

		//
		config.setAddRenegotiationInfoExtension(true);
		
		initialiseSession();		
	}

	/**
	 * Reset the connection with the TLS implementation by closing the current socket and initialising a new session
	 * 
	 * @throws IOException
	 */
	public void reset() throws IOException {
		close();
		initialiseSession();
	}
	
	/**
	 * Close the current connection
	 * @throws IOException 
	 */
	public void close() throws IOException {
		state.getTlsContext().getTransportHandler().closeConnection();
	}	
	
	/**
	 * Initialise a TLS connection by configuring a new context and connecting to the server 
	 * 
	 * @throws IOException
	 */
	public void initialiseSession() throws IOException {
		WorkflowTrace trace = new WorkflowTrace(config);
		state = new State(config, trace);

		TlsContext context = state.getTlsContext();
		
		//TransportHandler transporthandler = TransportHandlerFactory.createTransportHandler(config.getConnectionEnd());
		ConnectorTransportHandler transporthandler = new ConnectorTransportHandler(context.getConfig().getDefaultTimeout(), config.getConnectionEnd().getHostname(), config.getConnectionEnd().getPort());
		context.setTransportHandler(transporthandler);
		
		context.initTransportHandler();
        context.initRecordLayer();
	}
	
	/**
	 * Send the provided message to the TLS implementation
	 * 
	 * @param message ProtocolMessage to be sent
	 */
	protected void sendMessage(ProtocolMessage message) {
		List<ProtocolMessage> messages = new LinkedList<>();
		messages.add(message);
		new SendAction(messages).execute(state);
	}
    
	/**
	 * Receive message on the TLS connection
	 * 
	 * @return A string representation of the message types that were received
	 * @throws IOException
	 */
	protected String receiveMessages() throws IOException {
		// First check if the socket is still open
		if(state.getTlsContext().getTransportHandler().isClosed()) {
			return SYMBOL_CONNECTION_CLOSED;
		}
		
		List<String> receivedMessages = new LinkedList<>();
		ReceiveAction action = new ReceiveAction(new LinkedList<ProtocolMessage>());
		
		// Perform the actual receiving of the message
		action.execute(state);
		
		String outputMessage;
		
		// Iterate over all received messages and build a string containing their respective types
		for(ProtocolMessage message: action.getReceivedMessages()) {
			if(message.getProtocolMessageType() == ProtocolMessageType.ALERT) {
				AlertMessage alert = (AlertMessage)message;
				outputMessage = "ALERT_" + AlertLevel.getAlertLevel(alert.getLevel().getValue()).name() + "_" + AlertDescription.getName(alert.getDescription().getValue());
			}
			else {
				outputMessage = message.toCompactString();
			}
			receivedMessages.add(outputMessage);
		}
		
		if(state.getTlsContext().getTransportHandler().isClosed()) {
			receivedMessages.add(SYMBOL_CONNECTION_CLOSED);
		}
		
		if(receivedMessages.size() > 0) {
			return String.join("|", receivedMessages);
		} else {
			return "-";
		}
	}
	
	/**
	 * Send a message of the provided type and return the types of the response messages
	 * 
	 * @param inputSymbol A string indicating which type of message to send
	 * @return A string representation of the message types that were received
	 * @throws Exception 
	 */
	public String processInput(String inputSymbol) throws Exception {
		// Upon receiving the special input symbol RESET, we reset the system
		if(inputSymbol.equals(SYMBOL_RESET)) {
			reset();
			return "";			
		}
		
		// Check if the socket is already closed, in which case we don't have to bother trying to send data out
		if(state.getTlsContext().getTransportHandler().isClosed()) {
			return SYMBOL_CONNECTION_CLOSED;
		}

		// Process the regular input symbols
		switch(inputSymbol) {
		case "ClientHello":
			sendMessage(new ClientHelloMessage());
			break;
			
		case "ServerHello":
			sendMessage(new ServerHelloMessage());
			break;			
			
		case "Certificate":
			sendMessage(new CertificateMessage());
			break;
			
		case "CertificateRequest":
			sendMessage(new CertificateRequestMessage());
			break;
			
		case "DHEServerKeyExchange":
			sendMessage(new DHEServerKeyExchangeMessage());
			break;
			
		case "ServerHelloDone":
			sendMessage(new ServerHelloDoneMessage());
			break;
		
		case "RSAClientKeyExchange":
			sendMessage(new RSAClientKeyExchangeMessage());
			break;

		case "DHClientKeyExchange":
			sendMessage(new DHClientKeyExchangeMessage());
			break;
			
		case "ECDHClientKeyExchange":
			sendMessage(new ECDHClientKeyExchangeMessage());
			break;
			
		case "ChangeCipherSpec":
			sendMessage(new ChangeCipherSpecMessage());
			break;
			
		case "Finished":
			sendMessage(new FinishedMessage());
			break;
			
		case "ApplicationData":
			ApplicationMessage ad = new ApplicationMessage();
			ModifiableByteArray data = new ModifiableByteArray();
			data.setModification(ByteArrayModificationFactory.explicitValue("GET / HTTP/1.0\n".getBytes()));
			ad.setData(data);
			
			sendMessage(ad);
			break;
		
		default:
			throw new Exception("Unknown input symbol: " + inputSymbol);
		}
		
		return receiveMessages();
	}
	
	/**
	 * Start listening on the provided to port for a connection to provide input symbols and return output symbols. Only one connection is accepted at the moment.
	 * 
	 * @throws Exception 
	 */
	public void startListening() throws Exception {
		ServerSocket serverSocket = new ServerSocket(listenPort);
		System.out.println("Listening on port " + listenPort);
		
	    Socket clientSocket = serverSocket.accept();
	    clientSocket.setTcpNoDelay(true);
		
	    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
	    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

	    String input, output;
	    
	    while((input = in.readLine()) != null) {
	        output = processInput(input);
	        System.out.println(input + " / " + output);
	        out.println(output);
	        out.flush();
	    }	    
	    
	    clientSocket.close();
	    serverSocket.close();
	}
	
	public static void main(String ... argv) {
		try {
			TLSAttackerConnector connector = new TLSAttackerConnector();
			
			// Parse commandline arguments
	        JCommander commander = JCommander.newBuilder()
	        .addObject(connector)
            .build();
            commander.parse(argv);			

            if (connector.help) {
                commander.usage();
                return;
            }
            
            // Initialise the connector after the arguments are set
            connector.initialise();
            
            if(connector.test) {
    			System.out.println("ClientHello: " + connector.processInput("ClientHello"));

    			CipherSuite selectedCipherSuite = connector.state.getTlsContext().getSelectedCipherSuite();
    			if(selectedCipherSuite.name().contains("ECDH_")) {
        			System.out.println("ECDHClientKeyExchange: " + connector.processInput("ECDHClientKeyExchange"));    				
    			} else if(selectedCipherSuite.name().contains("DH_")) {
        			System.out.println("DHClientKeyExchange: " + connector.processInput("DHClientKeyExchange"));
    			} else if(selectedCipherSuite.name().contains("RSA_")) {
    				System.out.println("RSAClientKeyExchange: " + connector.processInput("RSAClientKeyExchange"));    				
    			}
    			System.out.println("ChangeCipherSpec: " + connector.processInput("ChangeCipherSpec"));
    			System.out.println("Finished: " + connector.processInput("Finished"));
    			System.out.println("ApplicationData: " + connector.processInput("ApplicationData"));
            	return;
            }

			connector.startListening();
		} catch(Exception e) {
			System.err.println("Error occured: " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}
}
