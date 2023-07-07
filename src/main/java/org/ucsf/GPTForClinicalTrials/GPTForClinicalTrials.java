package org.ucsf.GPTForClinicalTrials;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

/**
 * Hello world!
 *
 */
public class GPTForClinicalTrials 
{
	private static String PROMPTS = "prompts.txt";
	private static String OUTPUT = "output.txt";
	private static String COMPLETIONS = "completions.txt";
	private static String ERRORS = "errors.txt";
	private static String SKIPPED = "skipped.txt";
	
	private Properties props;
	private Path outputDirectory;
	private PrintStream out;
	private PrintStream completions;
	private PrintStream skipped;

    public static void main( String[] args )
    {
    	Properties props = new Properties();
    	try {
    		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    		InputStream is = classloader.getResourceAsStream("GPTForClinicalTrials.properties");	
    		InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
    		props.load(streamReader);
            System.out.println( props.toString() );
            
            // create the run directory and copy the prompts over
            String nowStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Timestamp(System.currentTimeMillis()));
            System.out.println(nowStr);
            
            GPTForClinicalTrials theApp = new GPTForClinicalTrials(props, props.getProperty("INPUT_FOLDER") + "\\" + args[0], props.getProperty("OUTPUT_FOLDER") + "\\" + nowStr);
            theApp.run(args.length > 1 ? Integer.parseInt(args[1]) : -1, args.length > 2 ? Integer.parseInt(args[2]) : -1);
            
            //trials.forEach(theApp::chatCompletionPrompt);
            
            //theApp.testSimpleChatPrompt();
            //theApp.chatCompletionPrompt(trials.get(0));
            //theApp.textCompletionPrompt(trials.get(1));        
        	//Consumer<GPTTrialSplash> processTrial = trial -> theApp.textCompletionPrompt(trial);
            //trials.forEach(processTrial);
            // print the unprocessed ones.
            
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }
    
    private GPTForClinicalTrials(Properties props, String inputFile, String outputDirectoryStr) throws IOException {
    	this.props = props;
    	this.outputDirectory = new File(outputDirectoryStr).toPath();
    	
    	new File(outputDirectoryStr).mkdirs();
        Files.copy(new File(inputFile).toPath(), new File(outputDirectoryStr + "\\" + PROMPTS).toPath());
        this.out = new PrintStream(new File(outputDirectoryStr + "\\" + OUTPUT));
        this.completions = new PrintStream(new File(outputDirectoryStr + "\\" + COMPLETIONS));
        this.skipped = new PrintStream(new File(outputDirectoryStr + "\\" + SKIPPED));
    }
    
    public void run(int limit, long sleep) throws IOException, InterruptedException {
        List<GPTTrialSplash> trials = parseFile(outputDirectory.toString() + "\\" + PROMPTS);

        OpenAiService service = getOpenAIService();      
        limit = limit < 0 ? trials.size() : Math.min(trials.size(), limit);
        for (int i = 0; i < limit || limit < 0; i++) {
        	System.out.println("Processing " + (i + 1) + " of " + limit);
            chatCompletionPrompt(service, trials.get(i));        	
        	if (sleep > 0) {
        		Thread.sleep(sleep * 1000);
        	}
        }
        service.shutdownExecutor();
    }
    
    public List<GPTTrialSplash> parseFile(String filename) throws IOException {
    	List<GPTTrialSplash> trials = new ArrayList<GPTTrialSplash>();
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			GPTTrialSplash trial = null;
			for (String line; (line = reader.readLine()) != null;) {
			    // Process line
				if (line.startsWith("#####")) {
					//System.out.println(line);
					// put complete one into trials;
					if (trial != null) {
						trials.add(trial);
					}				
					trial = new GPTTrialSplash(line.replace("##### Prompt for ", ""));
				}		
				else if (trial != null) {
					trial.prompt.add(line);
				}			
			}    
			if (trial != null) {
				trials.add(trial);
			}
		}
		return trials;
    }
    
    class GPTTrialSplash {
    	String header;
    	List<String> prompt;
    	List<String> response;
    	Exception ex;
    	
    	ChatCompletionRequest chatRequest;
    	ChatCompletionResult chatResult;
    	
    	
    	private GPTTrialSplash(String header) {
    		this.header = header;
    		this.prompt = new ArrayList<>();    			
    		this.response = new ArrayList<>();    			
    	}
    	
    	public String getPrompt() {
    		String promptStr = "";
    		for (String promptLn : prompt) {
    			promptStr += promptLn + System.lineSeparator();
    		}
    		return promptStr;
    	}
    	
		@Override
		public String toString() {
			return "GPTTrialSplash [header=" + header + System.lineSeparator() + "prompt=" + prompt + System.lineSeparator() + "response=" + response + 
					System.lineSeparator() + "ex=" + ex + System.lineSeparator() + "chatRequest=" + chatRequest + System.lineSeparator() + "chatResult=" + chatResult + "]";
		}
    	    	
    }
    
    private OpenAiService getOpenAIService() {
    	return new OpenAiService(props.getProperty("OPENAI_API_KEY"));
    }
    
    public void testSimplePrompt(String key) {
    	OpenAiService service = new OpenAiService(key);
    	CompletionRequest completionRequest = CompletionRequest.builder()
    	        .prompt("Somebody once told me the world is gonna roll me")
    	        .model("ada")
    	        .echo(true)
    	        .build();
    	service.createCompletion(completionRequest).getChoices().forEach(System.out::println);
    }
    
    public void testSimpleChatPrompt() {
    	OpenAiService service = getOpenAIService();
        System.out.println("Streaming chat completion...");
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "You are a dog and will speak as such.");
        messages.add(systemMessage);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .n(1)
                .maxTokens(50)
                .logitBias(new HashMap<>())
                .build();

//        service.streamChatCompletion(chatCompletionRequest)
//                .doOnError(Throwable::printStackTrace)
//                .blockingForEach(System.out::println);
        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(System.out::println);

        service.shutdownExecutor();
    }
    
//    public void textCompletionPrompt(GPTTrialSplash trial) {
//    	OpenAiService service = getOpenAIService();
//
//        System.out.println(trial.prompt.length() + " characters so about " + (trial.prompt.length()/4) + " tokens");
//    	CompletionRequest completionRequest = CompletionRequest.builder()
//    	        .prompt(trial.prompt)
//    	        .model("text-davinci-003")
//    	        .maxTokens(3000)
//    	        //.echo(true)
//    	        .build();
//    	try {
//        	List<CompletionChoice> choices = service.createCompletion(completionRequest).getChoices();
//        	//choices.forEach(System.out::println);
//        	Consumer<CompletionChoice> addReponse = a -> trial.response.add(a.getText());
//        	choices.forEach(addReponse);
//        	trial.printResponse(System.out);    		
//    	}
//    	catch (Exception e) {
//    		e.printStackTrace();
//    		trial.ex = e;
//    	}
//    }
    
    public void chatCompletionPrompt(OpenAiService service, GPTTrialSplash trial) {
        System.out.println("Trying chat completion for " + trial.header);
        out.println("##### " + trial.header);
        
        final List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), trial.getPrompt()));
        
        try {        	
	        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
	                .builder()
	                .model("gpt-3.5-turbo")
	                .messages(messages)
	                .n(1)
	                //.maxTokens(50)
	                //.logitBias(new HashMap<>())
	                .build();
	
	//        service.streamChatCompletion(chatCompletionRequest)
	//                .doOnError(Throwable::printStackTrace)
	//                .blockingForEach(System.out::println);
	        trial.chatRequest = chatCompletionRequest;
	        out.println(chatCompletionRequest);
	        trial.chatResult = service.createChatCompletion(chatCompletionRequest);
	        out.println(trial.chatResult);
	        
	        // if all is good
	        completions.println("##### Completion for " + trial.header);
	        for (ChatCompletionChoice choice : trial.chatResult.getChoices()) {
		        trial.response.add(choice.getMessage().getContent());
		        completions.println(choice.getMessage().getContent());
	        }
	        completions.println();
        }
    	catch (Exception e) {
    		e.printStackTrace();
    		e.printStackTrace(out);
    		trial.ex = e;
    		skipped.println("##### Prompt for " + trial.header);
    		skipped.println(trial.getPrompt());    		
    		skipped.println();
    	}        
        System.out.println(trial);
        out.println();
        
    }      
}
