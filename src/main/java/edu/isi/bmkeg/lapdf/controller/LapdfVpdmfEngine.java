package edu.isi.bmkeg.lapdf.controller;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.log4j.Logger;

import edu.isi.bmkeg.ftd.model.FTD;
import edu.isi.bmkeg.ftd.model.FTDPageImage;
import edu.isi.bmkeg.ftd.model.FTDRuleSet;
import edu.isi.bmkeg.lapdf.dao.LAPDFTextDao;
import edu.isi.bmkeg.lapdf.dao.vpdmf.LAPDFTextDaoImpl;
import edu.isi.bmkeg.lapdf.model.LapdfDocument;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.TextUtils;
import edu.isi.bmkeg.vpdmf.dao.CoreDao;
import edu.isi.bmkeg.vpdmf.dao.CoreDaoImpl;
import edu.isi.bmkeg.vpdmf.dao.VpdmfEngine;

/**
 * Basic Java API to high-level LAPDFText functionality, including:
 *
 * 1) Gathering layout statistics for the PDF file
 * 2) Running Block-based spatial chunker on PDF.
 * 3) Classifying texts of blocks in the file to categories based on a rule file.
 * 4) Outputting text or XML to file
 * 5) Rendering pages images of text layout or the original PDF file as PNG files
 * 6) Serializing LAPDFText object to a VPDMf database record.
 * 
 * @author burns
 *
 */
public class LapdfVpdmfEngine extends LapdfEngine implements VpdmfEngine  {

	private static Logger logger = Logger.getLogger(LapdfVpdmfEngine.class);

	private LAPDFTextDao ftdDao;
		

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public LapdfVpdmfEngine() 
			throws Exception {

		super();

	}

	public LapdfVpdmfEngine(File ruleFile) 
			throws Exception {

		super(ruleFile);
		
	}
	
	public LapdfVpdmfEngine(boolean imgFlag) 
			throws Exception  {

		super(imgFlag);
		
	}
	
	public LapdfVpdmfEngine(File ruleFile, boolean imgFlag) throws Exception {

		super(ruleFile, imgFlag);
		
	}	
	
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// VPDMf functions
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	/**
	 * Builds dao objects to input and output data to a VPDMf store.
	 */
	public void initializeVpdmfDao(String login, String password, String dbName) throws Exception {

		CoreDao coreDao = new CoreDaoImpl();
		coreDao.init(login, password, dbName);
		
		this.setFtdDao(new LAPDFTextDaoImpl(coreDao));
		this.getFtdDao().setCoreDao(coreDao);

	}
	
	public void addSwfToFtd(File pdf, FTD ftd) throws Exception, IOException {
		
		File swfBinDir = Converters.readAppDirectory("swftools");
		
		if( swfBinDir != null ) {

			String swfPath = swfBinDir + "/pdf2swf";
			if( System.getProperty("os.name").toLowerCase().contains("win") ) {
				swfPath += ".exe";
			}
			
			String pdfStem = pdf.getName().replaceAll("\\.pdf", "");
			pdfStem = pdfStem.replaceAll("\\s+", "_");
			File swfFile = new File( pdf.getParent() + "/" + pdfStem + ".swf" );
			
			Process p = Runtime.getRuntime().exec(swfPath + " " + pdf.getPath() 
					+ " -o " + swfFile.getPath());
			
			InputStream in = p.getInputStream();
			BufferedInputStream buf = new BufferedInputStream(in);
			InputStreamReader inread = new InputStreamReader(buf);
			BufferedReader bufferedreader = new BufferedReader(inread);
	        String line = "";
	        String out = "";
	        while ((line = bufferedreader.readLine()) != null) {
	        	out += line + "\n";
	        }
	        // Check for maven failure
	        try {
	        	if (p.waitFor() != 0) {
	        		out += "exit value = " + p.exitValue() + "\n";
	        	}
	        } catch (InterruptedException e) {
	        	out += "ERROR:\n" + e.getStackTrace().toString() + "\n";
	        } finally {
	        	// Close the InputStream
	        	bufferedreader.close();
	        	inread.close();
	        	buf.close();
	        	in.close();
			}
	        logger.debug(out);
	        
			if( !swfFile.exists() ) {
				return;
			}
			
			ftd.setLaswf( Converters.fileContentsToBytesArray(swfFile) );
			
		}
		
	}
	
	public void addPageImagesToFtd(FTD ftd, LapdfDocument doc, String stem, int lapdfMode) throws Exception, IOException {
		
		List<BufferedImage> imgList = this.buildPageImageList(doc, stem, lapdfMode);
		for( int i=0; i<imgList.size(); i++) {
			BufferedImage img = imgList.get(i);
			
			FTDPageImage ftdPgImg = new FTDPageImage();
			ftdPgImg.setPageImage(img);
			ftdPgImg.setPageNumber(i+1);
			
			ftd.getPages().add(ftdPgImg);
		}
		
	}
	
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// Rule Processing functions
	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public FTDRuleSet buildDrlRuleSet(String name, String description, File ruleFile) throws Exception {
	
		FTDRuleSet rs = new FTDRuleSet();
		rs.setRsName(name);
		rs.setRsDescription(description);
		rs.setFileName(ruleFile.getName());
		
		if( ruleFile.getName().endsWith(".drl") ) {
			
			rs.setRuleBody( TextUtils.readFileToString(ruleFile) );
		
		} else if( ruleFile.getName().endsWith(".xls") ) {
		
			rs.setExcelRuleFile(Converters.fileContentsToBytesArray(ruleFile));
			
		}
		
		return rs;
	
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	public LAPDFTextDao getFtdDao() {
		return ftdDao;
	}

	public void setFtdDao(LAPDFTextDao ftdDao) {
		this.ftdDao = ftdDao;
	}

}
