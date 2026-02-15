package net.dstone.common.utils;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

public class FileUtil {

	private static LogUtil logger = new LogUtil(FileUtil.class);
	
	public static int SEARCH_MODE_AND = 1;
	public static int SEARCH_MODE_OR = 2;

	public static int SEARCH_CASE_SENSITIVE = 1;
	public static int SEARCH_CASE_IGNORE = 2;

	public static String readFile(String filePath) {
		return readFile(filePath, null);
	}
	public static byte[] readFileByteArray(String filePath) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		java.io.FileInputStream fi = null;
		try {
			fi = new java.io.FileInputStream(filePath);
			int len = 0;
			byte[] buff = new byte[1024];
			while ((len = fi.read(buff)) != -1 ) {
				baos.write(buff, 0, len);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (fi != null){
				try {
					fi.close();
				} catch (Exception e) {
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
				}
			}
		}
		return baos.toByteArray();
	}
	public static String readFile(String filePath, String charSet) {
		
		String result = "";
		java.io.FileInputStream fi = null;
		java.io.InputStreamReader ir = null;
		java.io.BufferedReader buf = null;
		int lineNum = 0;
		try {
			fi = new java.io.FileInputStream(filePath);
			if(StringUtil.isEmpty(charSet)){
				ir = new java.io.InputStreamReader(fi);
			}else{
				ir = new java.io.InputStreamReader(fi, charSet);
			}
			buf = new java.io.BufferedReader(ir);
			String temp = null;
			while ((temp = buf.readLine()) != null) {
				result += temp + "\r\n";
				lineNum++;
			}
			// 1라인일 경우 쓸데없이 붙은 개행문자를 지워준다.
			if(lineNum == 1){
				result = result.substring(0, result.length()-2);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (buf != null){
				try {
					buf.close();
				} catch (Exception e) {
				}
			}
			if (ir != null){
				try {
					ir.close();
				} catch (Exception e) {
				}
			}
			if (fi != null){
				try {
					fi.close();
				} catch (Exception e) {
				}
			}
		}
		return result;
	}
	
	public static String[] readFileByLines(String filePath) {
		return readFileByLines(filePath, null);
	}
	
	public static String[] readFileByLines(String filePath, String charSet) {
		String[] lines = null;
		String line = null;
		ArrayList<String> lineArray = new ArrayList<String>();
		
		try (
			BufferedReader buf = new BufferedReader( 
				(StringUtil.isEmpty(charSet)?new InputStreamReader(new FileInputStream(filePath)):new InputStreamReader(new FileInputStream(filePath), charSet)) 
			)
		) {
			while ((line = buf.readLine()) != null) {
				lineArray.add(line);
			}
			lines = new String[lineArray.size()];
			lineArray.toArray(lines);
		} catch (Exception e) {
			e.printStackTrace();
		}
		lineArray.clear();
		lineArray = null;
		return lines;
	}
	
	public static String readFileLikeTail(String filePath, int lines) {
		String result = "";
		StringBuffer buff = new StringBuffer();

		File file = null;
		long fileLen = 0;
		RandomAccessFile raf = null;
		int readLines = 0;
		
		try {
			if( isFileExist(filePath) ){
				file = new File(filePath); 
				raf = new RandomAccessFile(file, "r");
				
				fileLen = file.length()-1;
				raf.seek(fileLen);
				for(long pointer = fileLen; pointer >=0; pointer--){
					raf.seek(pointer);
					char c;
					c = (char)raf.read();
					if( c == '\n' ){
						readLines++;
						if(readLines == lines){
							break;
						}
					}
					buff.append(c);
				}
				buff.reverse();
				result = buff.toString();
				result = new String(result.getBytes("8859_1")); 
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (raf != null){
				try {
					raf.close();
				} catch (Exception e) {
				}
			}
		}
		return result;
	}

	public static void deleteFile(String filePath) {

		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				f.delete();
			}
		} catch (Exception e) {
			logger.info(e.toString());
		}
	}
	public static void deleteDir(String filePath) {
		
		filePath = StringUtil.replace(filePath, "\\", "/");
		String[] fList = null;
		String f = null;
		try {
			if ( isFileExist(filePath) ) {
				if ( isDirectory(filePath) ) {
					fList = readFileListAll(filePath);
					if(fList != null){
						for(int i=0; i<fList.length; i++){
							f = fList[i];
							if ( isDirectory(f) ) {
								deleteDir(f);
							}else{
								deleteFile(f);
							}
						}
					}
					FileUtils.deleteDirectory(new File(filePath));
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
		}
	}

	public static boolean isFileExist(String filePath) {
		
		boolean bExist = false;

		try {
			filePath = StringUtil.replace(filePath, "\\", "/");
			java.io.File f = new java.io.File(filePath);
			bExist = f.exists();
		} catch (Exception e) {
			logger.info(e.toString());
			return false;
		}
		return bExist;
	}
	
	
	public static boolean isFileModified(String srcFile, String tgtFile) {
		boolean isModified = false;
		File tf = null;
		File sf = null;
		try {
			tf = new File(tgtFile);
			sf = new File(srcFile);
			
			if( !sf.exists() ||  !tf.exists()  ){
				isModified = true;
			}else{
				if( sf.exists() && tf.exists() ){
					if( sf.isFile() && tf.isFile() ){
						if( tf.lastModified() > sf.lastModified() ){
							isModified = true;
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return isModified;
	}
	public static boolean isDirectory(String filePath) {
		
		boolean bDirectory = false;
		
		try {
			filePath = StringUtil.replace(filePath, "\\", "/");
			java.io.File f = new java.io.File(filePath);
			if( f.exists() ){
				bDirectory = f.isDirectory();
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return false;
		}
		return bDirectory;
	}

	public static String[] readFileList(String filePath) {
		return readFileList(filePath, null, true);
	}
	
	public static String[] readFileList(String filePath, boolean extInclude) {
		return readFileList(filePath, null, extInclude);
	}
	
	public static String[] readFileList(String filePath, String filterStr) {
		return readFileList(filePath, filterStr, true);
	}
	
	
	public static String[] readFileList(String filePath, String filterStr, boolean extInclude) {
		String[] result = null;
		String fileName = null;
		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				if (f.isDirectory()) {
					if(StringUtil.isEmpty(filterStr)) {
						result = f.list();
					}else {
						result = f.list(new java.io.FilenameFilter(){
						    @Override 
						    public boolean accept(java.io.File dir, String name) { 
						         return (name.indexOf(filterStr)>-1 ? true : false); 
						    }
						});
					}
					if( result != null && !extInclude ) {
						for(int i=0; i<result.length ; i++) {
							fileName = result[i];
							if (fileName.indexOf(".") != -1) {
								fileName = fileName.substring(0, fileName.lastIndexOf("."));
							}
							result[i] = fileName;
						}
					}
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
		List<String> fileList = new ArrayList<String>();
		if(result != null) {
			for(String file : result) {
				if( !FileUtil.isDirectory(filePath + "/" + file ) ) {
					fileList.add(file);
				}
			}
		}
		result = fileList.toArray(new String[0]);
		fileList.clear();
		return result;
	}
	
	
	public static String[] readDirList(String filePath) {
		File[] dirs = null;
		ArrayList<String> list = new ArrayList<String>();
		String[] result = null;

		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				if (f.isDirectory()) {
					dirs = f.listFiles();
					if( dirs != null ){
						for( int i=0; i<dirs.length; i++ ){
							if (dirs[i].isDirectory()) {
								list.add(dirs[i].getName());
							}
						}
					}
				}
			}
			result = (String[])list.toArray(new String[list.size()]);
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
		return result;
	}
	


	public static void writeFile(String strPath, String strFileName, String strContents) {
		
		File f = null;
		FileOutputStream fout = null;
		BufferedWriter writer = null;
		try {
			makeDir(strPath);
			f = new File(strPath + System.getProperty("file.separator") + strFileName);
			fout = new FileOutputStream(f);
			writer = new BufferedWriter(new OutputStreamWriter(fout));
			writer.write(strContents);
			writer.flush();
		} catch (Exception e) {
			logger.info(e.toString());
			e.printStackTrace();
		} finally {
			if (fout != null)
				try {
					fout.close();
				} catch (Exception e) {
				}
			if (writer != null)
				try {
					writer.close();
				} catch (Exception e) {
				}
		}
	}
	
	public static void writeFile(String strPath, String strFileName, String strContents, String charSet) {
		
		File f = null;
		FileOutputStream fout = null;
		BufferedWriter writer = null;
		try {
			makeDir(strPath);
			f = new File(strPath + System.getProperty("file.separator") + strFileName);
			fout = new FileOutputStream(f);
			writer = new BufferedWriter(new OutputStreamWriter(fout, charSet));
			writer.write(strContents);
			writer.flush();
		} catch (Exception e) {
			logger.info(e.toString());
		} finally {
			if (fout != null)
				try {
					fout.close();
				} catch (Exception e) {
				}
			if (writer != null)
				try {
					writer.close();
				} catch (Exception e) {
				}
		}
	}
	
	public static void writeFileByteArray(String strPath, String strFileName, byte[] input) {
		
		File f = null;
		FileOutputStream fout = null;
		try {
			makeDir(strPath);
			f = new File(strPath + System.getProperty("file.separator") + strFileName);
			fout = new FileOutputStream(f);
			fout.write(input);
		} catch (Exception e) {
			logger.info(e.toString());
		} finally {
			if (fout != null){
				try {
					fout.close();
				} catch (Exception e) {
				}
			}
		}
	}


	public static void copyFile(String source, String target) {
		
		source = StringUtil.replace(source, "\\", "/");
		target = StringUtil.replace(target, "\\", "/");
		File sourceFile = new File(source);

		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;

		try {
			String targetPath = target.substring(0, target.lastIndexOf("/"));
			String sourcePath = target.substring(0, target.lastIndexOf("/"));
			makeDir(targetPath);
			makeDir(sourcePath);

			inputStream = new FileInputStream(sourceFile);
			outputStream = new FileOutputStream(target);

			int bytesRead = 0;
			byte[] buffer = new byte[1024];
			while ((bytesRead = inputStream.read(buffer, 0, 1024)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}

		} catch (Exception e) {
			logger.info(e.toString());
		} finally {
			try {
				outputStream.close();
			} catch (IOException ioe) {
			}
			try {
				inputStream.close();
			} catch (IOException ioe) {
			}
		}
	}
	

	public static void moveFile(String source, String target) {
		source = StringUtil.replace(source, "\\", "/");
		target = StringUtil.replace(target, "\\", "/");

		java.nio.file.Path sFile;
		java.nio.file.Path tFile;

		try {
			String targetPath = target.substring(0, target.lastIndexOf("/"));
			String sourcePath = target.substring(0, target.lastIndexOf("/"));
			
			makeDir(targetPath);
			makeDir(sourcePath);

			sFile = java.nio.file.Paths.get(source);
			tFile = java.nio.file.Paths.get(target);

			java.nio.file.Files.move(sFile, tFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		} catch (Exception e) {
			logger.info(e.toString());
		} 
	}


	public static String[] readFileListAll(String filePath) {
		return readFileListAll(filePath, null);
	}
	
	public static String[] readFileListAll(String filePath, String filterStr) {

		java.util.Vector<String> listVec = new java.util.Vector<String>();
		String[] result = new String[listVec.size()];
		String subFilePath = "";
		java.io.File tempFile = null;
		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				if (f.isDirectory()) {
					String[] subResult = null;
					if(StringUtil.isEmpty(filterStr)) {
						result = f.list();
					}else {
						result = f.list(new java.io.FilenameFilter(){
						    @Override 
						    public boolean accept(java.io.File dir, String name) { 
						         return (name.indexOf(filterStr)>-1 ? true : false); 
						    }
						});
					}
					if (result != null) {
						for (int i = 0; i < result.length; i++) {
							subFilePath = filePath + "/" + result[i];
							tempFile = new java.io.File(subFilePath);
							if (tempFile.isDirectory()) {
								subResult = readFileListAll(subFilePath);
								if (subResult != null) {
									for (int k = 0; k < subResult.length; k++) {
										listVec.add(subResult[k]);
									}
								}
							} else {
								listVec.add(subFilePath);
							}
						}
						result = new String[listVec.size()];
						listVec.copyInto(result);
						listVec.clear();
						listVec = null;
					}
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
		return result;
	}
	
	public static String[] readDirListAll(String filePath) {

		java.util.Vector<String> listVec = new java.util.Vector<String>();
		String[] result = new String[listVec.size()];
		String subFilePath = "";
		java.io.File tempFile = null;
		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				if (f.isDirectory()) {
					String[] subResult = null;
					result = f.list();
					if (result != null) {
						for (int i = 0; i < result.length; i++) {
							subFilePath = filePath + "/" + result[i];
							tempFile = new java.io.File(subFilePath);
							if (tempFile.isDirectory()) {
								listVec.add(subFilePath);
								subResult = readDirListAll(subFilePath);
								if (subResult != null) {
									for (int k = 0; k < subResult.length; k++) {
										listVec.add(subResult[k]);
									}
								}
							}
						}
						result = new String[listVec.size()];
						listVec.copyInto(result);
						listVec.clear();
						listVec = null;
					}
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
		return result;
	}


	public static String getFileName(String fileFullPath) {
		return getFileName(fileFullPath, true);
	}

	public static String getFileName(String fileFullPath, boolean extInclude) {
		String fileName = "";
		fileFullPath = StringUtil.replace(fileFullPath, "\\", "/");
		if (fileFullPath != null) {
			if (fileFullPath.indexOf("/") != -1) {
				fileName = fileFullPath.substring(fileFullPath.lastIndexOf("/") + 1);
			}else{
				fileName = fileFullPath;
			}
			if (!extInclude) {
				if (fileName.indexOf(".") != -1) {
					fileName = fileName.substring(0, fileName.lastIndexOf("."));
				}
			}
		}
		return fileName;
	}

	public static String getFilePath(String fileFullPath) {
		String filePath = "";
		fileFullPath = StringUtil.replace(fileFullPath, "\\", "/");
		if (fileFullPath != null) {
			if (fileFullPath.indexOf("/") != -1) {
				filePath = fileFullPath.substring(0,
						fileFullPath.lastIndexOf("/"));

			}
		}
		return filePath;
	}
	
	public static long getFileSize(String fileFullPath) {
		long size = 0;
		if( isFileExist(fileFullPath) ) {
			size = FileUtils.getFile(fileFullPath).length();
		}
		return size;
	}

	public static String getFileLastModiDate(String fileFullPath, String format) {
		String dateStr = "";
		if( isFileExist(fileFullPath) ) {
			java.io.File f = new java.io.File(fileFullPath);
			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTimeInMillis(f.lastModified());
			java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(format);
			dateStr = dateFormat.format(cal.getTime());
		}
		return dateStr;
	}
	

	public static String getFileExt(String fileFullPath) {
		String fileExt = "";
		if (fileFullPath != null && !"".equals(fileFullPath)) {
			if (fileFullPath.indexOf(".") != -1) {
				fileExt = fileFullPath.substring(fileFullPath.lastIndexOf(".") + 1);
			}
		}
		return fileExt;
	}
	

	public static boolean makeDir(String s) {
		
		boolean flag = false;
		s = StringUtil.replace(s, "/", System.getProperty("file.separator"));
		java.util.StringTokenizer stringtokenizer = new java.util.StringTokenizer(s, System.getProperty("file.separator"));
		String s3 = "";
		try {
			while (stringtokenizer.hasMoreTokens()) {
				String s2 = stringtokenizer.nextToken();
				s3 = s3 + "/" + s2;
				File file = new File(s3);
				if (!file.exists() || !file.isDirectory()) {
					file.mkdir();
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
		}
		return flag;
	}
	
	public static String[] searchFileList(String filePath, int searchMode, int searchCase, String[] keyword, String[] extFilter, boolean searchSaperatedOnly) {

		String[] result = new String[0];
		java.util.Vector<String> listVec = new java.util.Vector<String>();
		String[] fileList = null;
		String file = null;
		String fileConts = null;
		
		String key = null;
		String[] div = {" ", "\t", "\n", "\r", "(", ")", "{", "}", "[", "]", ".", ",", ";", "\"", "'", "/"};
		int keyCount = 0;
		int keyMatchCount = 0;
		
		boolean isExtFilterInclude = true;
		boolean isMatchKey = false;
		boolean isInclude = false;
		
		try {
			if(filePath == null || filePath.trim().equals("") ){
				throw new Exception("검색경로를 확인하세요.");
			}
			if(searchMode != SEARCH_MODE_AND && searchMode != SEARCH_MODE_OR){
				throw new Exception("검색모드를 확인하세요.");
			}
			if(keyword == null || keyword.length ==0 ){
				throw new Exception("키워드를 확인하세요.");
			}else{
				for(int k=0; k<keyword.length; k++){
					if(keyword[k] == null || keyword[k].equals("") ){
						throw new Exception("키워드를 확인하세요.");
					}
					if(searchSaperatedOnly){
						for(int l=0; l<div.length; l++){
							if(keyword[k].equals(div[l]) ){
								throw new Exception("문자["+keyword[k]+"]는 키워드로 올 수 없습니다.");
							}
						}
					}
				}
			}
			keyCount = keyword.length;

			fileList = readFileListAll(filePath);
			
			if(fileList != null){
				for(int i=0; i<fileList.length; i++){
					isInclude = false;
					file = fileList[i];
					if(extFilter != null){
						isExtFilterInclude = false;
						for(int k=0; k<extFilter.length; k++){
							if( FileUtil.getFileExt(file).toUpperCase().equals(extFilter[k].toUpperCase()) ){
								isExtFilterInclude = true;
								break;
							}
						}
					}
					if(!isExtFilterInclude){
						continue;
					}
					fileConts = readFile(file);
					//fileConts = StringUtil.replace(fileConts, "\r", "");
					if( searchCase == SEARCH_CASE_IGNORE){
						fileConts = fileConts.toUpperCase();
					}
					
					keyMatchCount = 0;
					for(int k=0; k<keyword.length; k++){
						key = keyword[k];
						if( searchCase == SEARCH_CASE_IGNORE ){
							key = key.toUpperCase();
						}
						isMatchKey = false;
						if(searchSaperatedOnly){
							for(int l=0; l<div.length; l++){
								for(int o=0; o<div.length; o++){
									if(fileConts.indexOf(div[l] + key + div[o]) != -1){
										isMatchKey = true;
										keyMatchCount++;
									}
									if(isMatchKey){break;}
								}
								if(isMatchKey){break;}
							}							
						}else{
							if(fileConts.indexOf(key) != -1){
								isMatchKey = true;
								keyMatchCount++;
							}
						}
					}
					if(searchMode == SEARCH_MODE_AND){
						if(keyCount == keyMatchCount){
							isInclude = true;
						}
					}else if(searchMode == SEARCH_MODE_OR){
						isInclude = true;
					}
					if(isInclude){
						listVec.add(file);
					}
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
		result = new String[listVec.size()];
		listVec.copyInto(result);
		listVec.clear();
		listVec = null;
		return result;
	}
	

	/**
	 * Given a source file, the charset encoding of the containing text, and the
	 * maximum split size allowed this method will split the source file into
	 * several individual files in byte sizes less than or equal to the
	 * splitMaxByteSize parameter while preserving integrity of individual
	 * lines.
	 * 
	 * Note that since line integrity is guaranteed the individual split files
	 * can be of different sizes since file splits always begin with a full line
	 * and end with a full line (i.e. no lines will be split across separate
	 * files).
	 * 
	 * @param sourceTextFile
	 *            The source file which contains textual data
	 * @param encoding
	 *            The charset encoding of the textual data in the source file
	 * @param splitMaxByteSize
	 *            The maximum size in bytes of the individual split files
	 * @return The set of split files created from the source file
	 * @throws Exception
	 */
	public static List<String> splitTextFile(String inputFileFullPath, String outputDir, int fileSizeByMB) throws Exception {
		int defaultCharBufferSize = 8192;
		String fileName = getFileName(inputFileFullPath);
		String fileExt = getFileExt(inputFileFullPath);
		File sourceTextFile = new File(inputFileFullPath);
		Charset encoding = Charset.defaultCharset();
		long sourceTotalByteSize = sourceTextFile.length();
		List<String> splitFiles = new ArrayList<String>();
		long splitMaxByteSize = fileSizeByMB * 1000 * 1000;
		
		if (sourceTotalByteSize <= splitMaxByteSize) {
			splitFiles.add(sourceTextFile.getAbsolutePath());
			return splitFiles;
		}
		// Calculate total number of split files
		int numSplitFiles = ((int) (sourceTotalByteSize / splitMaxByteSize)) + (splitMaxByteSize % sourceTotalByteSize > 0 ? 1 : 0);

		/**
		 * Maps the split file number to a byte index in the source file that
		 * represents the last byte stored in the split file.
		 */
		Map<Integer, Long> splitNumLastLineTotalBytes = new HashMap<Integer, Long>();

		Long totalByteSize = 0L;

		LineIterator it = null;
		FileInputStream sourceInputStream = new FileInputStream(sourceTextFile);
		int lastLineBreakByteSize = 0;
		try {
			FileUtil.LineBufferedReader advancedBufferReader = (new FileUtil()).new LineBufferedReader(new InputStreamReader(sourceInputStream, encoding), defaultCharBufferSize);
			it = IOUtils.lineIterator(advancedBufferReader);
			while (it.hasNext()) {
				char[] lastLineTerminators = advancedBufferReader.getLastLineTerminatorChars();
				lastLineBreakByteSize = new String(lastLineTerminators).getBytes(encoding).length;
				totalByteSize += it.nextLine().getBytes(encoding).length + lastLineBreakByteSize;
				int splitFileNum = (int) (totalByteSize / splitMaxByteSize) + 1;
				splitNumLastLineTotalBytes.put(splitFileNum, totalByteSize);
			}
		} finally {
			LineIterator.closeQuietly(it);
		}

		FileChannel sourceFileChannel = null;
		try (RandomAccessFile sourceTextFileRAM = new RandomAccessFile(sourceTextFile, "r")) {
			sourceFileChannel = sourceTextFileRAM.getChannel();
			int position = 0;
			Long lastTotalBytesOfLastLineForSplitFile = 0L;
			for (int i = 1; i <= numSplitFiles; i++) {
				Long totalBytesOfLastLineForSplitFile = splitNumLastLineTotalBytes.get(i);
				Long byteSize = totalBytesOfLastLineForSplitFile - lastTotalBytesOfLastLineForSplitFile;
				MappedByteBuffer mappedByteBuffer = sourceFileChannel.map(MapMode.READ_ONLY, position, byteSize);
				position = totalBytesOfLastLineForSplitFile.intValue();
				lastTotalBytesOfLastLineForSplitFile = totalBytesOfLastLineForSplitFile;

				File splitFile = new File(outputDir + "/" + fileName + "_" + i + "." + fileExt);
				if (splitFile.exists()){
					splitFile.delete();
				}

				try (RandomAccessFile splitFileRAM = new RandomAccessFile(splitFile, "rw")) {
					FileChannel splitFileChannel = splitFileRAM.getChannel();
					splitFileChannel.write(mappedByteBuffer);
					splitFiles.add(splitFile.getAbsolutePath());
					
				}
			}
		}
		return splitFiles;
	}
	class LineBufferedReader extends BufferedReader {

		private int defaultExpectedLineLength = 80;
		private Reader in;
		private char cb[];
		private int nChars, nextChar;
		private char lastLineTerminatorChars[];

		private static final int INVALIDATED = -2;
		private static final int UNMARKED = -1;
		private int markedChar = UNMARKED;
		private int readAheadLimit = 0; /* Valid only when markedChar > 0 */

		/** If the next character is a line feed, skip it */
		private boolean skipLF = false;

		/** The skipLF flag when the mark was set */
		private boolean markedSkipLF = false;

		/**
		 * Creates a buffering character-input stream that uses an input buffer
		 * of the specified size.
		 * @param in A Reader
		 * @param sz Input-buffer size
		 * @exception IllegalArgumentException If {@code sz <= 0}
		 */
		public LineBufferedReader(Reader in, int sz) {
			super(in);
			if (sz <= 0)
				throw new IllegalArgumentException("Buffer size <= 0");
			this.in = in;
			cb = new char[sz];
			nextChar = nChars = 0;
			lastLineTerminatorChars = new char[2];
		}

		/** Checks to make sure that the stream has not been closed */
		private void ensureOpen() throws IOException {
			if (in == null)
				throw new IOException("Stream closed");
		}

		/**
		 * Fills the input buffer, taking the mark into account if it is valid.
		 */
		private void fill() throws IOException {
			int dst;
			if (markedChar <= UNMARKED) {
				/* No mark */
				dst = 0;
			} else {
				/* Marked */
				int delta = nextChar - markedChar;
				if (delta >= readAheadLimit) {
					/* Gone past read-ahead limit: Invalidate mark */
					markedChar = INVALIDATED;
					readAheadLimit = 0;
					dst = 0;
				} else {
					if (readAheadLimit <= cb.length) {
						/* Shuffle in the current buffer */
						System.arraycopy(cb, markedChar, cb, 0, delta);
						markedChar = 0;
						dst = delta;
					} else {
						/* Reallocate buffer to accommodate read-ahead limit */
						char ncb[] = new char[readAheadLimit];
						System.arraycopy(cb, markedChar, ncb, 0, delta);
						cb = ncb;
						markedChar = 0;
						dst = delta;
					}
					nextChar = nChars = delta;
				}
			}
			int n;
			do {
				n = in.read(cb, dst, cb.length - dst);
			} while (n == 0);
			if (n > 0) {
				nChars = dst + n;
				nextChar = dst;
			}
		}

		/**
		 * Reads a single character.
		 * @return The character read, as an integer in the range 0 to 65535 (
		 *         <tt>0x00-0xffff</tt>), or -1 if the end of the stream has
		 *         been reached
		 * @exception IOException If an I/O error occurs
		 */
		public int read() throws IOException {
			synchronized (lock) {
				ensureOpen();
				for (;;) {
					if (nextChar >= nChars) {
						fill();
						if (nextChar >= nChars)
							return -1;
					}
					if (skipLF) {
						skipLF = false;
						if (cb[nextChar] == '\n') {
							nextChar++;
							continue;
						}
					}
					return cb[nextChar++];
				}
			}
		}

		/**
		 * Reads characters into a portion of an array, reading from the
		 * underlying stream if necessary.
		 */
		private int read1(char[] cbuf, int off, int len) throws IOException {
			if (nextChar >= nChars) {
				/*
				 * If the requested length is at least as large as the buffer,
				 * and if there is no mark/reset activity, and if line feeds are
				 * not being skipped, do not bother to copy the characters into
				 * the local buffer. In this way buffered streams will cascade
				 * harmlessly.
				 */
				if (len >= cb.length && markedChar <= UNMARKED && !skipLF) {
					return in.read(cbuf, off, len);
				}
				fill();
			}
			if (nextChar >= nChars)
				return -1;
			if (skipLF) {
				skipLF = false;
				if (cb[nextChar] == '\n') {
					nextChar++;
					if (nextChar >= nChars)
						fill();
					if (nextChar >= nChars)
						return -1;
				}
			}
			int n = Math.min(len, nChars - nextChar);
			System.arraycopy(cb, nextChar, cbuf, off, n);
			nextChar += n;
			return n;
		}

		/**
		 * Reads characters into a portion of an array.
		 *
		 * <p>
		 * This method implements the general contract of the corresponding
		 * <code>{@link Reader#read(char[], int, int) read}</code> method of the
		 * <code>{@link Reader}</code> class. As an additional convenience, it
		 * attempts to read as many characters as possible by repeatedly
		 * invoking the <code>read</code> method of the underlying stream. This
		 * iterated <code>read</code> continues until one of the following
		 * conditions becomes true:
		 * <ul>
		 *
		 * <li>The specified number of characters have been read,
		 *
		 * <li>The <code>read</code> method of the underlying stream returns
		 * <code>-1</code>, indicating end-of-file, or
		 *
		 * <li>The <code>ready</code> method of the underlying stream returns
		 * <code>false</code>, indicating that further input requests would
		 * block.
		 *
		 * </ul>
		 * If the first <code>read</code> on the underlying stream returns
		 * <code>-1</code> to indicate end-of-file then this method returns
		 * <code>-1</code>. Otherwise this method returns the number of
		 * characters actually read.
		 *
		 * <p>
		 * Subclasses of this class are encouraged, but not required, to attempt
		 * to read as many characters as possible in the same fashion.
		 *
		 * <p>
		 * Ordinarily this method takes characters from this stream's character
		 * buffer, filling it from the underlying stream as necessary. If,
		 * however, the buffer is empty, the mark is not valid, and the
		 * requested length is at least as large as the buffer, then this method
		 * will read characters directly from the underlying stream into the
		 * given array. Thus redundant <code>BufferedReader</code>s will not
		 * copy data unnecessarily.
		 *
		 * @param cbuf
		 *            Destination buffer
		 * @param off
		 *            Offset at which to start storing characters
		 * @param len
		 *            Maximum number of characters to read
		 *
		 * @return The number of characters read, or -1 if the end of the stream
		 *         has been reached
		 *
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		public int read(char cbuf[], int off, int len) throws IOException {
			synchronized (lock) {
				ensureOpen();
				if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
					throw new IndexOutOfBoundsException();
				} else if (len == 0) {
					return 0;
				}

				int n = read1(cbuf, off, len);
				if (n <= 0)
					return n;
				while ((n < len) && in.ready()) {
					int n1 = read1(cbuf, off + n, len - n);
					if (n1 <= 0)
						break;
					n += n1;
				}
				return n;
			}
		}

		/**
		 * Reads a line of text. A line is considered to be terminated by any
		 * one of a line feed ('\n'), a carriage return ('\r'), or a carriage
		 * return followed immediately by a linefeed.
		 *
		 * @param ignoreLF
		 *            If true, the next '\n' will be skipped
		 *
		 * @return A String containing the contents of the line, not including
		 *         any line-termination characters, or null if the end of the
		 *         stream has been reached
		 *
		 * @see java.io.LineNumberReader#readLine()
		 *
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		String readLine(boolean ignoreLF) throws IOException {
			StringBuffer s = null;
			int startChar;

			synchronized (lock) {
				ensureOpen();
				boolean omitLF = ignoreLF || skipLF;

				bufferLoop: for (;;) {

					if (nextChar >= nChars)
						fill();
					if (nextChar >= nChars) { /* EOF */
						if (s != null && s.length() > 0) {
							lastLineTerminatorChars = new char[0];
							return s.toString();
						} else
							return null;
					}
					boolean eol = false;
					char c = 0;
					int i;

					/* Skip a leftover '\n', if necessary */
					if (omitLF && (cb[nextChar] == '\n')) {
						nextChar++;
					}
					skipLF = false;
					omitLF = false;

					charLoop: for (i = nextChar; i < nChars; i++) {
						c = cb[i];
						if ((c == '\n') || (c == '\r')) {
							eol = true;
							lastLineTerminatorChars[0] = c;
							break charLoop;
						}
					}

					startChar = nextChar;
					nextChar = i;

					if (eol) {
						String str;
						if (s == null) {
							str = new String(cb, startChar, i - startChar);
						} else {
							s.append(cb, startChar, i - startChar);
							str = s.toString();
						}
						nextChar++;
						if (c == '\r') {
							if (nextChar < nChars)
								lastLineTerminatorChars[1] = cb[nextChar];
							skipLF = true;
						}
						return str;
					}

					if (s == null)
						s = new StringBuffer(defaultExpectedLineLength);
					s.append(cb, startChar, i - startChar);
				}
			}
		}

		/**
		 * Reads a line of text. A line is considered to be terminated by any
		 * one of a line feed ('\n'), a carriage return ('\r'), or a carriage
		 * return followed immediately by a linefeed.
		 *
		 * @return A String containing the contents of the line, not including
		 *         any line-termination characters, or null if the end of the
		 *         stream has been reached
		 *
		 * @exception IOException
		 *                If an I/O error occurs
		 *
		 * @see java.nio.file.Files#readAllLines
		 */
		public String readLine() throws IOException {
			return readLine(false);
		}

		/**
		 * Skips characters.
		 *
		 * @param n
		 *            The number of characters to skip
		 *
		 * @return The number of characters actually skipped
		 *
		 * @exception IllegalArgumentException
		 *                If <code>n</code> is negative.
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		public long skip(long n) throws IOException {
			if (n < 0L) {
				throw new IllegalArgumentException("skip value is negative");
			}
			synchronized (lock) {
				ensureOpen();
				long r = n;
				while (r > 0) {
					if (nextChar >= nChars)
						fill();
					if (nextChar >= nChars) /* EOF */
						break;
					if (skipLF) {
						skipLF = false;
						if (cb[nextChar] == '\n') {
							nextChar++;
						}
					}
					long d = nChars - nextChar;
					if (r <= d) {
						nextChar += r;
						r = 0;
						break;
					} else {
						r -= d;
						nextChar = nChars;
					}
				}
				return n - r;
			}
		}

		/**
		 * Tells whether this stream is ready to be read. A buffered character
		 * stream is ready if the buffer is not empty, or if the underlying
		 * character stream is ready.
		 *
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		public boolean ready() throws IOException {
			synchronized (lock) {
				ensureOpen();
				/*
				 * If newline needs to be skipped and the next char to be read
				 * is a newline character, then just skip it right away.
				 */
				if (skipLF) {
					/*
					 * Note that in.ready() will return true if and only if the
					 * next read on the stream will not block.
					 */
					if (nextChar >= nChars && in.ready()) {
						fill();
					}
					if (nextChar < nChars) {
						if (cb[nextChar] == '\n')
							nextChar++;
						skipLF = false;
					}
				}
				return (nextChar < nChars) || in.ready();
			}
		}

		/**
		 * Tells whether this stream supports the mark() operation, which it
		 * does.
		 */
		public boolean markSupported() {
			return true;
		}

		/**
		 * Marks the present position in the stream. Subsequent calls to reset()
		 * will attempt to reposition the stream to this point.
		 *
		 * @param readAheadLimit
		 *            Limit on the number of characters that may be read while
		 *            still preserving the mark. An attempt to reset the stream
		 *            after reading characters up to this limit or beyond may
		 *            fail. A limit value larger than the size of the input
		 *            buffer will cause a new buffer to be allocated whose size
		 *            is no smaller than limit. Therefore large values should be
		 *            used with care.
		 *
		 * @exception IllegalArgumentException
		 *                If {@code readAheadLimit < 0}
		 * @exception IOException
		 *                If an I/O error occurs
		 */
		public void mark(int readAheadLimit) throws IOException {
			if (readAheadLimit < 0) {
				throw new IllegalArgumentException("Read-ahead limit < 0");
			}
			synchronized (lock) {
				ensureOpen();
				this.readAheadLimit = readAheadLimit;
				markedChar = nextChar;
				markedSkipLF = skipLF;
			}
		}

		/**
		 * Resets the stream to the most recent mark.
		 *
		 * @exception IOException
		 *                If the stream has never been marked, or if the mark
		 *                has been invalidated
		 */
		public void reset() throws IOException {
			synchronized (lock) {
				ensureOpen();
				if (markedChar < 0)
					throw new IOException((markedChar == INVALIDATED) ? "Mark invalid" : "Stream not marked");
				nextChar = markedChar;
				skipLF = markedSkipLF;
			}
		}

		public void close() throws IOException {
			synchronized (lock) {
				if (in == null)
					return;
				try {
					in.close();
				} finally {
					in = null;
					cb = null;
				}
			}
		}

		/**
		 * Returns a {@code Stream}, the elements of which are lines read from
		 * this {@code BufferedReader}. The {@link Stream} is lazily populated,
		 * i.e., read only occurs during the
		 * <a href="../util/stream/package-summary.html#StreamOps">terminal
		 * stream operation</a>.
		 *
		 * <p>
		 * The reader must not be operated on during the execution of the
		 * terminal stream operation. Otherwise, the result of the terminal
		 * stream operation is undefined.
		 *
		 * <p>
		 * After execution of the terminal stream operation there are no
		 * guarantees that the reader will be at a specific position from which
		 * to read the next character or line.
		 *
		 * <p>
		 * If an {@link IOException} is thrown when accessing the underlying
		 * {@code BufferedReader}, it is wrapped in an
		 * {@link UncheckedIOException} which will be thrown from the
		 * {@code Stream} method that caused the read to take place. This method
		 * will return a Stream if invoked on a BufferedReader that is closed.
		 * Any operation on that stream that requires reading from the
		 * BufferedReader after it is closed, will cause an UncheckedIOException
		 * to be thrown.
		 *
		 * @return a {@code Stream<String>} providing the lines of text
		 *         described by this {@code BufferedReader}
		 *
		 * @since 1.8
		 */
		public Stream<String> lines() {
			Iterator<String> iter = new Iterator<String>() {
				String nextLine = null;

				@Override
				public boolean hasNext() {
					if (nextLine != null) {
						return true;
					} else {
						try {
							nextLine = readLine();
							return (nextLine != null);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
				}

				@Override
				public String next() {
					if (nextLine != null || hasNext()) {
						String line = nextLine;
						nextLine = null;
						return line;
					} else {
						throw new NoSuchElementException();
					}
				}
			};
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED | Spliterator.NONNULL), false);
		}

		/**
		 * <p>
		 * Returns a char array containing at most two characters which
		 * identifies the line terminator of the last line returned by a call to
		 * the readLine() method.
		 * 
		 * @return char array containing one of four possibilities detailed
		 *         below.
		 *         <p>
		 *         1. For lines ending with a Carriage Return character ('\r') :
		 *         <p>
		 *         char[0] = '\r'
		 *         <p>
		 *         2. For lines ending with a Line Feed character ('\n') :
		 *         <p>
		 *         char[0] = '\n'
		 *         <p>
		 *         3. For lines ending with Carriage Return character
		 *         immediately followed by a Line Feed character ('\r\n') :
		 *         <p>
		 *         char[0] = '\r'
		 *         <p>
		 *         char[1] = '\n'
		 *         <p>
		 *         4. For line without any line terminator (i.e. last line in
		 *         the file) an empty char array will be returned.
		 */
		public char[] getLastLineTerminatorChars() {
			return lastLineTerminatorChars;
		}
	}
	

	/**
	 * 파일을 멀티파트로 변경하는 메소드.
	 * @param filePath
	 * @return
	 */
	public static MultipartFile toMultiFile(String filePath) {
		MultipartFile multipartFile = null;
		try {
			if( isFileExist(filePath) ) {
				File file = new File(filePath);
				multipartFile = new org.springframework.mock.web.MockMultipartFile("file", file.getName(), Files.probeContentType(file.toPath()), new FileInputStream(file)); 
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return multipartFile;
	}
	
	/**
	 * 파일을 바이트배열자원으로 변경하는 메소드.<br>
	 * HttpClient 를 이용해서 파일을 muti-part로 보낼때 필요한 기능.<br>
	 * 예)<br>
	 * Map<String, Object> fileMap = new HashMap<String, Object>();<br>
	 * fileMap.put("name", "홍길동");<br>
	 * fileMap.put("filePart1", toByteArrayResource("file01", "C:/Temp/테스트01.txt"));<br>
	 * fileMap.put("filePart2", toByteArrayResource("file02", "C:/Temp/테스트02.txt"));<br>
	 * request = this.getEntity(MediaType.MULTIPART_FORM_DATA, 헤더맵, fileMap);<br>
	 * response = this.getRestTemplate().postForEntity(URL, request, 리턴타입.class);<br>
	 * @param fileName
	 * @param fileFullPath
	 * @return
	 * @throws Exception
	 */
	public static ByteArrayResource toByteArrayResource(String fileName, String fileFullPath) throws Exception  {
		MultipartFile multiFile = FileUtil.toMultiFile(fileFullPath);
		ByteArrayResource contentsAsResource = new ByteArrayResource(multiFile.getBytes()) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};
		return contentsAsResource;
	}
	
	/**
	 * 파일명이 중복일때 숫자를 붙여서 새로운 파일명 생성
	 * @param toFilePath
	 * @return
	 */
	public static String getNewFileName(String toFilePath) {
		String dest = toFilePath;
		String dot = ".";

		if (StringUtil.isEmpty(toFilePath))
			return dest;
		
		if(!Files.exists(Paths.get(dest))) 
	    		return dest;
	    
	    try {
	    	File f = new File(toFilePath);
	    	String fileHead = f.getName();
	    	String fileExt = "";
	    	
	    	int pos = f.getName().lastIndexOf(dot);
	    	if(pos > -1) {
			fileHead = f.getName().substring(0, f.getName().lastIndexOf("."));
			fileExt = f.getName().substring(f.getName().lastIndexOf(".")+1);
	    	} else {
	    		dot = "";
	    	}

		for (int i = 1; i < Integer.MAX_VALUE; i++) {
			dest = Paths.get(f.getParent(), String.format("%s(%d)%s%s", fileHead, i, dot, fileExt)).toString();
			if (!Files.exists(Paths.get(dest)))
			break;
		}
		    
	    } catch(IllegalStateException ie ) {}
	    
	    return dest;
	}

	/**
	 * PDF 파일명을 (페이지별)jpg 파일로 변환하는 메소드
	 * @param pdfPath PDF파일경로
	 * @param toPath 페이지별jpg가 생성될 디렉토리
	 * @return
	 */
	public static void convertPdfToJpg(String pdfPath, String toPath) throws IOException {
	    File file = new File(pdfPath);
	    PDDocument document = null;
	    try {
	    	document = PDDocument.load(file);
		    PDFRenderer pdfRenderer = new PDFRenderer(document);
		    makeDir(toPath);
		    for (int page = 0; page < document.getNumberOfPages(); ++page) {
		        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
		        File outputfile = new File(toPath + "/" + page + ".jpg");
		        ImageIO.write(bim, "jpg", outputfile);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(document != null) {
				document.close();
			}
		}
	}

	/**
	 * Image파일을 Base64스트링으로 변환하는 메소드
	 * @param imagePath
	 * @return
	 */
	public static String convertImageToBase64(String imagePath) throws IOException {
		byte[] fileContent = FileUtils.readFileToByteArray(new File(imagePath));
		String encodedString = Base64.getEncoder().encodeToString(fileContent);
		return encodedString;
	}

	/**
	 * Base64스트링을 Image파일로 변환하는 메소드
	 * @param imagePath
	 * @param encodedString
	 * @return
	 */
	public static void convertBase64ToImage(String imagePath, String encodedString) throws IOException {
		makeDir(getFilePath(imagePath));
		byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
		FileUtils.writeByteArrayToFile(new File(imagePath), decodedBytes);
	}
	
	/**
	 * PDF 파일명을 (페이지별)BufferedImage로 변환하는 메소드
	 * @param pdfPath PDF파일경로
	 * @return
	 */
	public static List<BufferedImage> convertPdfToBufferedImage(String pdfPath) throws IOException {
		List<BufferedImage> images = new ArrayList<BufferedImage>();
	    File file = new File(pdfPath);
	    PDDocument document = null;
	    try {
	    	document = PDDocument.load(file);
		    PDFRenderer pdfRenderer = new PDFRenderer(document);
		    for (int page = 0; page < document.getNumberOfPages(); ++page) {
		        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
		        images.add(bim);
		    }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(document != null) {
				document.close();
			}
		}
	    return images;
	}

	/**
	 * BufferedImage 파일을 Base64 스트링으로 변환하는 메소드
	 * @param image
	 * @return
	 */
	public static String convertBufferedImageToBase64(BufferedImage image) throws IOException {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ImageIO.write(image, "jpg", baos);
	    byte[] imageBytes = baos.toByteArray();
	    return Base64.getEncoder().encodeToString(imageBytes);
	}

	/**
	 * 파일목록을 Zip으로 압축하는 메소드
	 * @param zipFilePath
	 * @param fullFilePathArray
	 * @return
	 */
	public static void zip(String zipFilePath, String[] fullFilePathArray) throws Exception {
		if( fullFilePathArray != null && fullFilePathArray.length > 0 ) {

	        try (
	        	FileOutputStream fos = new FileOutputStream(zipFilePath);
	            ZipOutputStream zipOut = new ZipOutputStream(fos);
	         ) {
	        	
				for(String fullFilePath : fullFilePathArray) {
			        try (
			        	FileInputStream fis = new FileInputStream(fullFilePath)
				    ) {
			        	ZipEntry zipEntry = new ZipEntry(fullFilePath);
			        	zipOut.putNextEntry(zipEntry);
			            byte[] buffer = new byte[1024];
			            int len;
			            while ((len = fis.read(buffer)) >= 0) {
			                zipOut.write(buffer, 0, len);
			            }
					} catch (Exception e) {
						e.printStackTrace();
					} 
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
	}

	/**
	 * Zip파일을 압축해제하는 메소드
	 * @param zipFilePath
	 * @param destDir
	 * @return
	 */
	public static void unZip(String zipFilePath, String destDir) throws Exception {
		if( FileUtil.isFileExist(zipFilePath) ) {
	        byte[] buffer = new byte[1024];
	        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
	            ZipEntry zipEntry;
	            while ((zipEntry = zis.getNextEntry()) != null) {
	                File newFile = new File(destDir, zipEntry.getName());
	                new File(newFile.getParent()).mkdirs();

	                try (FileOutputStream fos = new FileOutputStream(newFile)) {
	                    int len;
	                    while ((len = zis.read(buffer)) > 0) {
	                        fos.write(buffer, 0, len);
	                    }
	                }
	            }
	        }
		}
	}
	
	/**
	 * 파일의 라인을 카운트하는 메소드
	 * @param zipFilePath
	 * @param destDir
	 * @return
	 */
	public static long countLines(String filePath) {
        try (Stream<String> stream = Files.lines(Paths.get(filePath))) {
            return stream.count();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
