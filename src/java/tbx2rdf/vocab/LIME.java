package tbx2rdf.vocab;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;


/**
 * @author Andrea Turbati
 */
public class LIME {
	private static Model defaultModel = ModelFactory.createDefaultModel(); 
	
	public static Resource Lexicon = defaultModel.createProperty("http://www.w3.org/ns/lemon/lime#Lexicon");
	public static Property entry = defaultModel.createProperty("http://www.w3.org/ns/lemon/lime#entry");
	public static Property language = defaultModel.createProperty("http://www.w3.org/ns/lemon/lime#language");
	
}
