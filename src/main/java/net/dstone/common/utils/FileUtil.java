package net.dstone.common.utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

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
		
		java.io.FileInputStream fi = null;
		java.io.InputStreamReader ir = null;
		java.io.BufferedReader buf = null;
		try {
			fi = new java.io.FileInputStream(filePath);
			if(StringUtil.isEmpty(charSet)){
				ir = new java.io.InputStreamReader(fi);
			}else{
				ir = new java.io.InputStreamReader(fi, charSet);
			}
			buf = new java.io.BufferedReader(ir);
			while ((line = buf.readLine()) != null) {
				lineArray.add(line);
			}
			lines = new String[lineArray.size()];
			lineArray.toArray(lines);
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
			lineArray.clear();
			lineArray = null;
		}
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
					deleteFile(filePath);
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
		
		String[] result = null;

		try {
			java.io.File f = new java.io.File(filePath);
			if (f.exists()) {
				if (f.isDirectory()) {
					result = f.list();
				}
			}
		} catch (Exception e) {
			logger.info(e.toString());
			return null;
		}
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
	
	public static void sortFile(String inputFileFullPath, String outputFileFullPath) {
		try {
			sortFile(inputFileFullPath, outputFileFullPath, com.google.code.externalsorting.ExternalSort.defaultcomparator);
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}
	public static void sortFile(String inputFileFullPath, String outputFileFullPath, Comparator<String> cmp) {
		try {
			com.google.code.externalsorting.ExternalSort.sort(new File(inputFileFullPath), new File(outputFileFullPath), cmp);
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}
	
	public static void mergeFiles(String inputDir, String outputFileFullPath) {
		mergeFiles(inputDir, outputFileFullPath, com.google.code.externalsorting.ExternalSort.defaultcomparator);
	}
	
	public static void mergeFiles(String inputDir, String outputFileFullPath, Comparator<String> cmp) {
		List<File> files = new ArrayList<File>();
		try {
			String[] fileList = readFileListAll(inputDir) ;
			if(fileList != null){
				for(String file : fileList){
					files.add(new File(file));
				}
			}
			com.google.code.externalsorting.ExternalSort.mergeSortedFiles(files, new File(outputFileFullPath), cmp);
		} catch (Exception e) {
			logger.error(e.toString());
		}
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
			net.dstone.common.utils.resources.LineBufferedReader advancedBufferReader = new net.dstone.common.utils.resources.LineBufferedReader(new InputStreamReader(sourceInputStream, encoding), defaultCharBufferSize);
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
	
}
