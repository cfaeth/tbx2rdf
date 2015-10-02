package tbx2rdf;

import tbx2rdf.types.TBX_Terminology;
import com.hp.hpl.jena.rdf.model.Model;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.xml.sax.SAXException;

/**
 * Main class for TBX2RDF Converter.
 * WRONG DESIGN: IT IS CURRENTLY MIXING TBX2RDF AND IATE AFFAIRS
 * 
 * Entry point of the functionality, it parses the parameters and invokes the conversion methods 
 * making them available from the command line.
 * Example of params for the command line: samples/iate.xml --output samples/iate.nt --big=true
 * Another example: samples/CounterSample.xml --output=samples/CounterSample.nt
 *  --output samples/iatefullmini.nt
 * 
 * It is advice to set a parameter in the Java Virtual Machine: -Dfile.encoding=UTF-8 in order to have good character encoding.
 * 
 * @author John McCrae - Universität Bielefeld
 * @author Victor Rodriguez - Universidad Politécnica de Madrid 
 */
public class Main {

    private final static Logger logger = Logger.getLogger(Main.class);
    //Determines whether it will be a stream-parsing (if big=true) or a block conversion (big=false)
    static boolean big = false;
    // Establishes the file with the mappings
    static String mapping_file = "mappings.default";
    // Input file name to be read from;
    static String input_file = "";
    // Output file name to be written to
    static String output_file = "";
    // If the output is to be shown in console
    static boolean bOutputInConsole = true;
    //Determines if the parsing is going to be lenient or strict
    public static boolean lenient = false;
    // The mappings to be used
    public static Mappings mappings;
    // The base namespace of the dataset
    public static String DATA_NAMESPACE = "http://tbx2rdf.lider-project.eu/data/iate/";
    

    /**
     * Main method. 
     */
    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException {
        PropertyConfigurator.configure("log4j.properties");

        boolean ok = parseParams(args);
        if (!ok) {
            return;
        }
        
        //READ MAPPINGS
        logger.info("Using mapping file: " + mapping_file + "\n");
        mappings = Mappings.readInMappings(mapping_file);

        if (big) {
            convertBigFile();
        } else {
            convertSmallFile();
        }

    }

    /**
     * Parses the command line parameters
     */
    public static boolean parseParams(String[] args) {
        String ejecutando = "";
        for (String ejecutandox : args) {
            ejecutando += " " + ejecutandox;
        }
        logger.info(ejecutando);
        if (args.length == 0) {
            System.out.println("Usage: TBX2RDF_Converter <INPUT_FILE> (--output=<OUTPUT_FILE>)? (--mappings=<MAPPING_FILE>)? (--big=true)? (--datanamespace=<DATA_NAMESPACE>)?");
            System.out.println("If no OUTPUT_FILE is provided, then <OUTPUT FILE>s/.xml/.rdf/ will be assumed as output file.");
            System.out.println("If no MAPPING_FILE is provided, then mappings.default will be used.");
            return false;
        }
        input_file = args[0];                                           //First argument, input file
        File file = new File(input_file);
        if (!file.exists())
        {
            logger.error("The file " + input_file + " does not exist");
            return false;
        }
        
        output_file = input_file.replaceAll("\\.(xml|tbx)", "\\.rdf");
        if (!output_file.endsWith(".rdf")) {
            output_file += ".rdf";
        }
        String arg, key, value;
        for (int i = 1; i < args.length; i++) {
            arg = args[i];
            Pattern p = Pattern.compile("^--(output|mappings|datanamespace|big|lenient)=(.*?)$");
            Matcher matcher;
            matcher = p.matcher(arg);
            if (matcher.matches()) {
                key = matcher.group(1);
                value = matcher.group(2);
                if (key.equals("output")) {
                    output_file = value;
                    bOutputInConsole = false;
                    logger.info("OUTPUT_FILE set to" + output_file + "\n");
                }
                if (key.equals("mappings")) {
                    mapping_file = value;
                    logger.info("MAPPING_FILE set to" + mapping_file + "\n");
                }
                if (key.equals("datanamespace")) {
                    DATA_NAMESPACE = value;
                    logger.info("DATA_NAMESPACE set to" + DATA_NAMESPACE + "\n");
                }
                if (key.equals("big")) {
                    if (value.equals("true")) {
                        big = true;
                    }
                    logger.info("Processing large file");
                }
                if (key.equals("lenient")) {
                    if (value.equals("true")) {
                        lenient = true;
                    }
                    logger.info("Processing in lenient mode");
                }
            }
        }
        return true;
    }

    /**
     * This is the conversion to be invoked for large files, that will be processed in a stream
     * The output is serialized as the conversion is being done     
     */
    public static boolean convertBigFile() {
        try {
            bOutputInConsole = false;
            logger.info("Doing the conversion of a big file\n");
            TBX2RDF_Converter converter = new TBX2RDF_Converter();
            PrintStream fos;
            if (output_file.isEmpty() || bOutputInConsole) {
                fos = System.out;
            } else {
                fos = new PrintStream(output_file, "UTF-8");
            }
            if (fos == null) {
                logger.error("output file could not be open");
                return false;
            }
            converter.convertAndSerializeLargeFile(input_file, fos, mappings);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Standard conversion
     * This is the conversion invoked from the web service. 
     * Input file is read as a whole and kept in memory.
     */
    public static boolean convertSmallFile() {
        try {
            logger.info("Doing the standard conversion (not a big file)\n");
            //READ TBX XML
            logger.info("Opening file " + input_file + "\n");
            BufferedReader reader = new BufferedReader(new FileReader(input_file));
            TBX2RDF_Converter converter = new TBX2RDF_Converter();
            TBX_Terminology terminology = converter.convert(reader, mappings);
            //WRITE. This one has been obtained from 
            logger.info("Writting output to " + output_file + "\n");
//            final Model model = terminology.getModel("file:" + output_file);
            final Model model = terminology.getModel(Main.DATA_NAMESPACE);           
            RDFDataMgr.write(new FileOutputStream(output_file), model, Lang.TURTLE);
            reader.close();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
        return true;

    }
}
