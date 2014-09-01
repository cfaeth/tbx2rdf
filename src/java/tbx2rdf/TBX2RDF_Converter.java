package tbx2rdf;

import tbx2rdf.types.LexicalEntry;
import tbx2rdf.types.Describable;
import tbx2rdf.types.MartifHeader;
import tbx2rdf.types.TBX_Terminology;
import tbx2rdf.types.Descrip;
import tbx2rdf.types.XReference;
import tbx2rdf.types.Lexicon;
import tbx2rdf.types.Term;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import tbx2rdf.types.AdminGrp;
import tbx2rdf.types.AdminInfo;
import tbx2rdf.types.DescripGrp;
import tbx2rdf.types.DescripNote;
import tbx2rdf.types.MartifHeader.*;
import tbx2rdf.types.Note;
import tbx2rdf.types.NoteLinkInfo;
import tbx2rdf.types.Reference;
import tbx2rdf.types.TermComp;
import tbx2rdf.types.TermCompGrp;
import tbx2rdf.types.TermCompList;
import tbx2rdf.types.TermNote;
import tbx2rdf.types.TermNoteGrp;
import tbx2rdf.types.TransacGrp;
import tbx2rdf.types.TransacNote;
import tbx2rdf.types.Transaction;
import tbx2rdf.types.abs.impID;
import tbx2rdf.types.abs.impIDLangTypeTgtDtyp;

/**
 * Entry point of the TBX2RDF converter
 *
 * TBX: framework consisting of a core structure, and a formalism (eXtensible
 * Constraint Specification) for identifying a set of data-categories and their
 * constraints, both expressed in XML
 *
 * Several of the remaining data categories, including definition, context, part
 * of speech, and subject field are very important and should be included in a
 * terminology whenever possible. The most important non-mandatory data category
 * is part of speech.
 *
 *
 * A very nice reference for the basic model can be found here:
 * http://www.terminorgs.net/downloads/TBX_Basic_Version_3.pdf
 *
 * @author Philipp Cimiano - Universität Bielefeld
 * @author Victor Rodriguez - Universidad Politécnica de Madrid
 */
public class TBX2RDF_Converter {

    /**
     * Do not construct
     */
    public TBX2RDF_Converter() {
    }

    /**
     * Converts a TBX string into a RDF. Parses the XML searching for termEntry
     * elements.
     *
     * Then, Serializes Terms and Lexicons
     *
     * @param str The TBX XML as a String.
     * @return str A Turtle string with the equivalent information
     */
    public String convert(String str, Mappings mappings, String resourceURI) throws Exception {
        TBX_Terminology result = convert(new StringReader(str), mappings);
        StringWriter sw = new StringWriter();
        RDFDataMgr.write(sw, result.getModel(resourceURI), RDFFormat.TURTLE_PRETTY);
        return sw.toString();
    }

    /**
     *
     */
    public TBX_Terminology convert(Reader input, Mappings mappings) throws IOException, SAXException, ParserConfigurationException, TBXFormatException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        //  InputStream stream = new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));

        Document doc = db.parse(new InputSource(input));

        // extract here martif metadata
        Element root = doc.getDocumentElement();

        return processMartif(root, mappings);

    }

    TBX_Terminology processMartif(Element root, Mappings mappings) throws IOException, SAXException {
        final TBX_Terminology terminology = new TBX_Terminology(root.getAttribute("type"), processMartifHeader(child(root, "martifHeader"), mappings));
        mappings.defaultLanguage = "en";
        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("text")) {
                terminology.terms.addAll(processText(e, mappings));
            } else if (!e.getTagName().equalsIgnoreCase("martifHeader")) {
                unexpected(root);
            }
        }

        return terminology;
    }

    MartifHeader processMartifHeader(Element root, Mappings mappings) throws IOException, SAXException {
        final MartifHeader header = new MartifHeader(processFileDescrip(child(root, "fileDesc"), mappings));
        processID(header, root);

        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("encodingDesc")) {
                header.encodingDesc = e.getChildNodes();
            } else if (e.getTagName().equalsIgnoreCase("revisionDesc")) {
                header.revisionDesc = e.getChildNodes();
            } else if (!e.getTagName().equalsIgnoreCase("fileDesc")) {
                unexpected(e);
            }
        }

        return header;
    }

    FileDesc processFileDescrip(Element root, Mappings mappings) throws IOException, SAXException {
        final FileDesc fileDesc = new FileDesc();

        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("titleStmt")) {
                fileDesc.titleStmt = processTitleStmt(e, mappings);
            } else if (e.getTagName().equalsIgnoreCase("publicationStmt")) {
                fileDesc.publicationStmt = e;
            } else if (e.getTagName().equalsIgnoreCase("sourceDesc")) {
                fileDesc.sourceDesc.add(e);
            } else {
                unexpected(e);
            }

        }
        return fileDesc;

    }

    private TitleStmt processTitleStmt(Element root, Mappings mappings) {
        final TitleStmt titleStmt = new TitleStmt(child(root, "title").getTextContent());
        if (root.hasAttribute("xml:lang")) {
            titleStmt.lang = root.getAttribute("xml:lang");
        }
        if (root.hasAttribute("id")) {
            titleStmt.id = root.getAttribute("id");
        }
        final Element title = child(root, "title");
        if (title.hasAttribute("xml:lang")) {
            titleStmt.title_lang = title.getAttribute("xml:lang");
        }
        if (root.hasAttribute("id")) {
            titleStmt.title_id = title.getAttribute("id");
        }
        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("note")) {
                titleStmt.notes.add(e);
            }
        }

        return titleStmt;
    }

    Collection<Term> processText(Element root, Mappings mappings) throws IOException, SAXException {
        final Collection<Term> terms = new HashSet<Term>();
        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("body")) {
                terms.addAll(processBody(e, mappings));
            } else if (e.getTagName().equalsIgnoreCase("back")) {
                terms.addAll(processBack(e, mappings));
            } else {
                unexpected(e);
            }

        }
        return terms;
    }

    private Collection<? extends Term> processBody(Element root, Mappings mappings) {
        final Collection<Term> terms = new HashSet<Term>();
        for (Element e : children(root)) {
            if (e.getTagName().equalsIgnoreCase("termEntry")) {
                terms.add(processTermEntry(e, mappings));
            } else {
                unexpected(e);
            }
        }
        return terms;
    }

    private Collection<? extends Term> processBack(Element root, Mappings mappings) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    
   Term processTermEntry(Element node, Mappings mappings) {
        // create new Term 
        // add subjectField
        // add ID

    	// <!ELEMENT termEntry  ((%auxInfo;),(langSet+)) >
        // <!ATTLIST termEntry
        // id ID #IMPLIED >
    	// <!ENTITY % auxInfo '(descrip | descripGrp | admin | adminGrp | transacGrp | note | ref | xref)*' >
        Term term = new Term();

        int langsetcount = 0;

        processID(term, node);

        for (Element sub : children(node)) {
            final String name = sub.getTagName();

            if (name.equalsIgnoreCase("langSet")) {
                langsetcount++;
                this.processLangSet(term, sub, mappings);
            } else {
                processAuxInfo(term, sub, mappings);
            }
        }

        if (langsetcount == 0) {
            throw new TBXFormatException("No langSet element in termEntry");
        }

        return term;
    }

    void processReference(NoteLinkInfo descr, Element sub, Mappings mappings) {
    	// <!ELEMENT ref (#PCDATA) >
        // <!ATTLIST ref
        //    %impIDLangTypTgtDtyp;
        // >

    	//<!ENTITY % impIDLangTypTgtDtyp ' id ID #IMPLIED
        //xml:lang CDATA #IMPLIED 
        // type CDATA #REQUIRED 
        // target IDREF #IMPLIED 
        // datatype CDATA #IMPLIED
        //'>
        final Reference ref = new Reference(processType(sub, mappings,true), sub.getAttribute("xml:lang"), mappings);
        if(sub.hasAttribute("id")) {
            ref.setID(sub.getAttribute("id"));
        }
        if(sub.hasAttribute("target")) {
            ref.target = sub.getAttribute("target");
        }
        if(sub.hasAttribute("datatype")) {
            ref.datatype = sub.getAttribute("datatype");
        }
        descr.References.add(ref);
    }

    void processAdminGrp(NoteLinkInfo descr, Element node, Mappings mappings) {
		// <!ELEMENT adminGrp (admin, (adminNote|note|ref|xref)*) >
        // <!ATTLIST adminGrp
        // id ID #IMPLIED >


        processID((impID)descr, node);
        
        int i = 0;
        for(Element tig_child : children(node)) {

            String name = tig_child.getNodeName();

            if (i == 0 && !name.equals("admin")) {
                throw new TBXFormatException("First element of TIG is not term !\n");
            }

            if (name.equals("admin")) {
                processAdmin(descr, tig_child, mappings);
            } else if (name.equals("adminNote")) {
                processAdminGrp(descr, tig_child, mappings);
            } else if (name.equals("note")) {
                processNote(descr, tig_child, mappings);
            } else if (name.equals("ref")) {
                this.processReference(descr, tig_child, mappings);
            } else if (name.equals("xref")) {
                this.processXReference(descr, tig_child, mappings);
            } else {
                throw new TBXFormatException("Element " + name + "not defined by TBX standard");
            }
            i++;
        }

    }

    /**
     * Processes the langset (xml:lang)
     *
     * @return a LexicalEntry
     */
    void processLangSet(Term term, Element langSet, Mappings mappings) {

    	// <!ELEMENT langSet ((%auxInfo;), (tig | ntig)+) >
        // <!ATTLIST langSet
        // id ID #IMPLIED
        // xml:lang CDATA #REQUIRED >

        LexicalEntry entry;

        int termCount = 0;

        processID(term, langSet);

        String language = getValueOfAttribute(langSet, "xml:lang");

        if (language == null) {
            throw new TBXFormatException("Language not specified for langSet!");
        }


        for(Element sub : children(langSet)) {

            final String name = sub.getNodeName();

            if (name.equals("ntig")) {
                termCount++;
                entry = new LexicalEntry(language, mappings);
                this.processNTIG(entry, sub, mappings);
                term.Lex_entries.add(entry);
            } else if (name.equals("tig")) {
                termCount++;
                entry = new LexicalEntry(language, mappings);
                this.processTIG(entry, sub, mappings);
                term.Lex_entries.add(entry);
            } else {
                processAuxInfo(term, sub, mappings);
            }
        }

        if (termCount == 0) {
            throw new TBXFormatException("No TIG nor NTIG in langSet !");
        }

    }

    void processTIG(LexicalEntry entry, Element tig, Mappings mappings) {

    	// <!ELEMENT tig (term, (termNote)*, %auxInfo;) >
        // <!ATTLIST tig
        // id ID #IMPLIED >
        int i = 0;

        processID(entry, tig);

        for(Element tig_child : children(tig)) {

            String name = tig_child.getNodeName();

            if (i == 0 && !name.equals("term")) {
                throw new TBXFormatException("First element of TIG is not term !\n");
            }

            if (name.equals("term")) {
                this.processTerm(entry, tig_child, mappings);
            } else if (name.equals("termNote")) {
                entry.TermNotes.add(new TermNoteGrp(this.processTermNote(tig_child, mappings), mappings.defaultLanguage, mappings));
            } else {
                processAuxInfo(entry, tig, mappings);
            }
            i++;
        }

    }

    void processTerm(LexicalEntry entry, Element node,
            Mappings mappings) {

		// <!ELEMENT term %basicText; >
        // <!ATTLIST term
        // id ID #IMPLIED >
        entry.Lemma = node.getTextContent();
    }

    TermNote processTermNote(Element tig_child, Mappings mappings) {

		// <!ELEMENT termNote %noteText; >
        // <!ATTLIST termNote
        //    %impIDLangTypTgtDtyp;
        // >
		// <!ENTITY % impIDLangTypTgtDtyp ' id ID #IMPLIED
        // xml:lang CDATA #IMPLIED type CDATA #REQUIRED target IDREF #IMPLIED datatype CDATA #IMPLIED
        // '>
        final TermNote note = new TermNote(tig_child.getChildNodes(), processType(tig_child, mappings, true), tig_child.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(note, tig_child, mappings);
        return note;
    }

    void processNTIG(LexicalEntry entry, Node ntig, Mappings mappings) {

		// <!ELEMENT ntig (termGrp, %auxInfo;) >
        // <!ATTLIST ntig
        // id ID #IMPLIED	
        // >
        int i = 0;
        for(Element ntig_child : children(ntig)) {

            String name = ntig_child.getNodeName();

            if (i == 0 && !name.equals("termGrp")) {
                throw new TBXFormatException("First element of NTIG is not termGrp !\n");
            }

            if (name.equals("termGrp")) {
                this.processTermGroup(entry, ntig_child, mappings);
            } else {
                processAuxInfo(entry, ntig_child, mappings);
            }
            i++;
        }
    }

    void processXReference(NoteLinkInfo descr, Element node, Mappings mappings) {

		// <!ELEMENT xref (#PCDATA) >
        // <!ATTLIST xref
        // %impIDType;
        // target CDATA #REQUIRED >
        XReference xref = new XReference(getValueOfAttribute(node, "target"), node.getTextContent());

        processID(xref, node);
        xref.type = processType(node, mappings, false);
        descr.Xreferences.add(xref);
    }

    void processDescripGroup(Describable descr, Element node, Mappings mappings) {

        // The DTD for a DescripGroup is as follows
        // <!ELEMENT descripGrp (descrip, (descripNote|admin|adminGrp|transacGrp|note|ref|xref)*)
        // >
        // <!ATTLIST descripGrp
        //  id ID #IMPLIED >
        
        DescripGrp descrip = new DescripGrp(processDescrip(firstChild("descrip", node), mappings));
        processID(descrip, node);
        // get first child that needs to be a descrip
        // process other children that can be: descripNote, admin, adminGroup, transacGrp, note, ref and xref
        for(Element sub : children(node)) {
            final String name = sub.getTagName();
            if(name.equalsIgnoreCase("descrip")) {
                // ignore
            } else if(name.equalsIgnoreCase("descripNote")) {
                processDescripNote(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("admin")) {
                this.processAdmin(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("adminGrp")) {
                this.processAdminGrp(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("transacGrp")) {
                this.processTransactionGroup(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("note")) {
                this.processTransactionGroup(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("ref")) {
                this.processReference(descrip, sub, mappings);
            } else if (name.equalsIgnoreCase("xref")) {
                this.processXReference(descrip, sub, mappings);
            } else {
                throw new TBXFormatException("Unexpected subnode " + node.getTagName());
            }
        }
        
        descr.Descriptions.add(descrip);
    }

    void processAdmin(NoteLinkInfo descr, Element node, Mappings mappings) {
        // <!ELEMENT admin %noteText; >
        // <!ATTLIST admin
        //  %impIDLangTypTgtDtyp;
        //>
        final AdminInfo admin = new AdminInfo(node.getChildNodes(), processType(node, mappings, true), node.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(admin, node, mappings);
        descr.AdminInfos.add(new AdminGrp(admin));
    }

    /**
     * Processes a Transaction Group www.isocat.org/datcat/DC-162 A transacGrp
     * element can contain either one transacNote element, or one date element,
     * or both. Example:
     * <transacGrp>
     * <transac type="transactionType">creation</transac>
     * <transacNote type="responsibility" target="CA5365">John
     * Harris</transacNote>
     * <date>2008‐05‐12</date>
     * </transacGrp>
     *
     * @param transacGroup A Transaction group in XML // According to the TBX
     * DTD, a transacGroup looks as follows: // <!ELEMENT transacGrp (transac,
     * (transacNote|date|note|ref|xref)* ) >
     * // <!ATTLIST transacGrp // id ID #IMPLIED >
     * // Transaction transaction = new Transaction(lex);
     */
    void processTransactionGroup(NoteLinkInfo descr, Element elem, Mappings mappings) {

    	// <!ELEMENT transacGrp (transac, (transacNote|date|note|ref|xref)* ) >
        // <!ATTLIST transacGrp
        // id ID #IMPLIED >
        final TransacGrp transacGrp = new TransacGrp(processTransac(firstChild("transac", elem), mappings));

        int i = 0;
        for(Element child : children(elem)) {

            String name = child.getNodeName();

            if (i == 0 && !name.equals("transac")) {
                throw new TBXFormatException("First element of transacGrp is not termGrp !\n");
            }

            if (name.equals("transac")) {
                //processTransac(transacGrp, child, mappings);
            } else if (name.equals("transacNote")) {
                processTransacNote(transacGrp, child, mappings);
            } else if (name.equals("date")) {
                processDate(transacGrp, child, mappings);
            } else if (name.equals("note")) {
                processNote(transacGrp, child, mappings);
            } else if (name.equals("xref")) {
                processXReference(transacGrp, child, mappings);
            } else if (name.equals("ref")) {
                this.processReference(transacGrp, child, mappings);
            } else {
                throw new TBXFormatException("Element " + name + " not defined by TBX standard\n");
            }
            i++;
        }
        descr.Transactions.add(transacGrp);
    }

    void processTermGroup(LexicalEntry entry, Element node, Mappings mappings) {
        // <!ELEMENT termGrp (term, (termNote|termNoteGrp)*, (termCompList)* ) >
        // <!ATTLIST termGrp
        //  id ID #IMPLIED
        //>
        for(Element elem : children(node)) {
           final String name = elem.getTagName();
           if(name.equalsIgnoreCase("term")) {
               processTerm(entry, node, mappings);
           } else if(name.equalsIgnoreCase("termNote")) {
               entry.TermNotes.add(new TermNoteGrp(processTermNote(elem, mappings), mappings.defaultLanguage, mappings));
           } else if(name.equalsIgnoreCase("termNoteGrp")) {
               entry.TermNotes.add(processTermNoteGrp(elem, mappings));
           } else if(name.equalsIgnoreCase("termCompList")) {
               processTermCompList(entry, elem, mappings);
           }
        }
    }

    void processNote(NoteLinkInfo descr, Element elem, Mappings mappings) {
        //<!ELEMENT note %noteText; >
        //<!ATTLIST note %impIDLang;
        //>
        final Note note = new Note(elem.getChildNodes(), elem.getAttribute("xml:lang"), mappings);
        processID(note, elem);
        descr.notes.add(note);
    }

    Descrip processDescrip(Element elem, Mappings mappings) {
        //<!ELEMENT descrip %noteText; >
           //<!ATTLIST descrip
           //%impIDLangTypTgtDtyp;
           //>
        final Descrip descrip = new Descrip(elem.getChildNodes(), processType(elem, mappings, true), elem.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(descrip, elem, mappings);
        return descrip;
    }

    void processDescripNote(DescripGrp descrip, Element sub, Mappings mappings) {
           // <!ELEMENT descripNote (#PCDATA) >
           //<!ATTLIST descripNote
           //%impIDLangTypTgtDtyp;
           //> 
        final DescripNote descripNote = new DescripNote(sub.getChildNodes(), processType(sub, mappings, true), sub.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(descripNote, sub, mappings);
        descrip.descripNote.add(descripNote);
    }

    Transaction processTransac(Element child, Mappings mappings) {
           //  <!ELEMENT transac (#PCDATA) >
           //<!ATTLIST transac
           //%impIDLangTypTgtDtyp;
           //>
        final Transaction transaction = new Transaction(child.getTextContent(), processType(child, mappings, true), child.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(transaction, child, mappings);
        return transaction;
    }

    void processTransacNote(TransacGrp transacGrp, Element child, Mappings mappings) {
    
           //<!ELEMENT transacNote (#PCDATA) >
           //<!ATTLIST transacNote
           //%impIDLangTypTgtDtyp;
           //> 
        final TransacNote transacNote = new TransacNote(child.getTextContent(), processType(child, mappings, true), child.getAttribute("xml:lang"), mappings);
        processImpIDLangTypeTgtDType(transacNote, child, mappings);
        transacGrp.transacNotes.add(transacNote);
    }

    void processDate(TransacGrp transacGrp, Element child, Mappings mappings) {
           //  <!ELEMENT date (#PCDATA) >
           //<!ATTLIST date
           //id ID #IMPLIED
           //> 
        transacGrp.date = child.getTextContent();
    }

    TermNoteGrp processTermNoteGrp(Element elem, Mappings mappings) {
           //  <!ELEMENT termNoteGrp (termNote, %noteLinkInfo;) >
           //<!ATTLIST termNoteGrp
           //id ID #IMPLIED
           //> 
       final TermNoteGrp termNoteGrp = new TermNoteGrp(processTermNote(firstChild("termNote", elem)
               , mappings), elem.getAttribute("xml:lang"), mappings);
       for(Element e : children(elem))  {
            final String name = e.getTagName();
            if(name.equalsIgnoreCase("termNote")) {
                // Do nothing
            } else if(name.equalsIgnoreCase("admin")) {
                processAdmin(termNoteGrp, e, mappings);
            } else if(name.equalsIgnoreCase("adminGrp")) {
                processAdminGrp(termNoteGrp, e, mappings);
             } else if(name.equalsIgnoreCase("transacGrp")) {
                processTransactionGroup(termNoteGrp, e, mappings);
             } else if(name.equalsIgnoreCase("note")) {
                processNote(termNoteGrp, e, mappings);
             } else if(name.equalsIgnoreCase("ref")) {
                processReference(termNoteGrp, e, mappings);
             } else if(name.equalsIgnoreCase("xref")) {
                processXReference(termNoteGrp, e, mappings);
             }
       }
       return termNoteGrp;
    }

    void processTermCompList(LexicalEntry entry, Element elem, Mappings mappings) {
           // <!ELEMENT termCompList ((%auxInfo;), (termComp | termCompGrp)+) >
           //<!ATTLIST termCompList
           //id ID #IMPLIED
           //type CDATA #REQUIRED
           //>
        final TermCompList termCompList = new TermCompList(mappings.getMapping("termCompList", "type", elem.getAttribute("type")));
        processID(termCompList, elem);
        for(Element e : children(elem)) {
            final String name = e.getTagName();
            if(name.equalsIgnoreCase("termComp")) {
                final TermComp termComp = processTermComp(e, mappings);
                termCompList.termComp.add(new TermCompGrp(termComp, null, mappings));
            } else if(name.equalsIgnoreCase("termCompGrp")) {
                processTermCompGrp(termCompList, e, mappings);
            } else if(name.equalsIgnoreCase("admin")) {
                processAdmin(termCompList, e, mappings);
            } else if(name.equalsIgnoreCase("adminGrp")) {
                processAdminGrp(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("transacGrp")) {
                processTransactionGroup(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("note")) {
                processNote(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("ref")) {
                processReference(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("xref")) {
                processXReference(termCompList, e, mappings);
             } 
        }
        entry.Decomposition.add(termCompList);
        
    }

    TermComp processTermComp(Element e, Mappings mappings) {
        //<!ELEMENT termComp (#PCDATA) >
        //<!ATTLIST termComp
        // %impIDLang;
        //>
        final TermComp termComp = new TermComp(e.getTextContent(), e.getAttribute("xml:lang"), mappings);
        processID(termComp, e);
        return termComp;
    }

    void processTermCompGrp(TermCompList termCompList, Element elem, Mappings mappings) {
        //<!ELEMENT termCompGrp (termComp, (termNote|termNoteGrp)*, %noteLinkInfo;) >
        //<!ATTLIST termCompGrp
        //id ID #IMPLIED
        //>
        final TermCompGrp termCompGrp = new TermCompGrp(processTermComp(firstChild("termComp", elem), mappings), null, mappings);
        for(Element e : children(elem)) {
            final String name = e.getTagName();
            if(name.equalsIgnoreCase("termNote")) {
                termCompGrp.termNoteGrps.add(new TermNoteGrp(processTermNote(e, mappings), null, mappings));
            } else if(name.equalsIgnoreCase("termNoteGrp")) {
                termCompGrp.termNoteGrps.add(processTermNoteGrp(e, mappings));
            } else if(name.equalsIgnoreCase("admin")) {
                processAdmin(termCompList, e, mappings);
            } else if(name.equalsIgnoreCase("adminGrp")) {
                processAdminGrp(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("transacGrp")) {
                processTransactionGroup(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("note")) {
                processNote(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("ref")) {
                processReference(termCompList, e, mappings);
             } else if(name.equalsIgnoreCase("xref")) {
                processXReference(termCompList, e, mappings);
            }
        }
    }



    /**
     * Gets the value of an XML attribute
     *
     * @param node XML attribute
     * @param name Name of the attribute
     * @return Value of the attribute
     */
    private String getValueOfAttribute(Node node, String name) {

        NamedNodeMap map = node.getAttributes();

        Node namedItem = map.getNamedItem(name);

        if (namedItem != null) {
            return namedItem.getNodeValue();
        } else {
            throw new TBXFormatException("Node " + node.getNodeName() + " does not have expected attribute " + name);
        }

    }

    private Iterable<Element> children(Node n) {
        final List<Element> e = new ArrayList<Element>();
        final NodeList ns = n.getChildNodes();
        for (int i = 0; i < ns.getLength(); i++) {
            if (ns.item(i) instanceof Element) {
                e.add((Element) ns.item(i));
            }
        }
        return e;
    }

    private Element child(Node n, String tagName) {
        final NodeList ns = n.getChildNodes();
        for (int i = 0; i < ns.getLength(); i++) {
            if (ns.item(i) instanceof Element && ((Element) ns.item(i)).getTagName().equalsIgnoreCase(tagName)) {
                return (Element) ns.item(i);
            }
        }
        throw new TBXFormatException("Expected " + tagName);
    }

    private void unexpected(Node n) {
        if (n instanceof Element) {
            throw new TBXFormatException("Unexpected " + ((Element) n).getTagName());
        } else {
            throw new TBXFormatException("Unexpected");
        }
    }

    private void processID(impID elem, Element node) {
        if(node.hasAttribute("id")) {
            elem.setID(node.getAttribute("id"));
        }
    }

    private void processImpIDLangTypeTgtDType(impIDLangTypeTgtDtyp ref, Element sub, Mappings mappings) {
       // <!ENTITY % impIDLangTypTgtDtyp '
       //  id ID #IMPLIED
       //  xml:lang CDATA #IMPLIED
       //  type CDATA #REQUIRED
       //  target IDREF #IMPLIED
       //  datatype CDATA #IMPLIED
       // '>
        if(sub.hasAttribute("id")) {
            ref.setID(sub.getAttribute("id"));
        }
        if(sub.hasAttribute("target")) {
            ref.target = sub.getAttribute("target");
        }
        if(sub.hasAttribute("datatype")) {
            ref.datatype = sub.getAttribute("datatype");
        }
    }

    private void processAuxInfo(Describable term, Element sub, Mappings mappings) {
       //   <!ENTITY % auxInfo '(descrip | descripGrp | admin | adminGrp | transacGrp | note | ref
       //        | xref)*' >
        final String name = sub.getTagName();
        if (name.equalsIgnoreCase("descrip")) {
            term.Descriptions.add(new DescripGrp(processDescrip(sub, mappings)));
        } else if (name.equalsIgnoreCase("descripGrp")) {
            this.processDescripGroup(term, sub, mappings);
        } else if (name.equalsIgnoreCase("admin")) {
            this.processAdmin(term, sub, mappings);
        } else if (name.equalsIgnoreCase("adminGrp")) {
            this.processAdminGrp(term, sub, mappings);
        } else if (name.equalsIgnoreCase("transacGrp")) {
            this.processTransactionGroup(term, sub, mappings);
        } else if (name.equalsIgnoreCase("note")) {
            this.processNote(term, sub, mappings);
        } else if (name.equalsIgnoreCase("ref")) {
            this.processReference(term, sub, mappings);
        } else if (name.equalsIgnoreCase("xref")) {
            this.processXReference(term, sub, mappings);
        } else {
            throw new TBXFormatException("Element " + name + " not defined by TBX standard");
        }
        
    }

    public Element firstChild(String name, Element node) {
        final NodeList nl = node.getElementsByTagName(name);
        if(nl.getLength() > 0) {
            return (Element)nl.item(0);
        } else {
            throw new TBXFormatException("Expected child named " + name);
        }
    }

    private Mapping processType(Element sub, Mappings mappings, boolean required) {
        if(sub.hasAttribute("type")) {
            return mappings.getMapping(sub.getTagName(), "type", sub.getAttribute("type"));
        } else if(required) {
            throw new TBXFormatException("type expected");
        } else {
            return null;
        }
    }
}