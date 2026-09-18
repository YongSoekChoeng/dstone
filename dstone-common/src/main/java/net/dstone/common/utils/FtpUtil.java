package net.dstone.common.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Vector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import com.jcraft.jsch.Session;

import net.dstone.common.config.ConfigProperty;
import net.dstone.common.core.BaseObject;

/**
 * @author Default
 *
 */
/*****************************************************************
<< 인증관련 작업 >>
	
	1. Open SSL 서버 설정
		1-1. C:\ProgramData\ssh\sshd_config 수정
			PubkeyAuthentication yes
			PasswordAuthentication yes   # 테스트 시 yes / 완전히 키 인증만 허용 시 no
	2. 인증 생성		
		<CMD>
			ssh-keygen -t rsa -b 4096 -m PEM
		
		<Power Shell>
			type $env:USERPROFILE\.ssh\id_rsa.pub >> $env:USERPROFILE\.ssh\authorized_keys
			icacls $env:USERPROFILE\.ssh /inheritance:r
			icacls $env:USERPROFILE\.ssh /grant "$($env:USERNAME):R"
			icacls $env:USERPROFILE\.ssh\authorized_keys /inheritance:r
			icacls $env:USERPROFILE\.ssh\authorized_keys /grant "$($env:USERNAME):R"
		
		<CMD>
			ssh %USERNAME%@localhost
			ssh -i %USERPROFILE%/.ssh/id_rsa %USERNAME%@localhost
		
*****************************************************************/
@Component
public class FtpUtil extends BaseObject {

	@Autowired
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	public Session session = null;
	public Channel channel = null;
	public ChannelSftp channelSftp = null;

	/**
	 * SFTP 접속
	 */
	public void connect() throws Exception {
		String remoteHost = configProperty.getProperty("sftp.host");
		String remotePort = configProperty.getProperty("sftp.port");
		String username = configProperty.getProperty("sftp.username");
		String password = configProperty.getProperty("sftp.password");
		String privateKeyPath = configProperty.getProperty("sftp.private-key-path");
		this.connect(remoteHost, remotePort, username, password, privateKeyPath);
	}
	
	/**
	 * SFTP 접속
	 * 
	 * @param remoteHost
	 * @param remotePort
	 * @param username
	 * @param password
	 * @param privateKeyPath
	 */
	public void connect(String ip, String port, String id, String pw, String privateKey) throws Exception {
		String connIp = ip; // 접속 SFTP 서버 IP
		int connPort = Integer.parseInt(port); // 접속 PORT
		String connId = id; // 접속 ID
		String connPw = pw; // 접속 PW
		int timeout = 10000; // 타임아웃 10초

		JSch jsch = new JSch();
		try {
			jsch.setLogger(new Logger(){
				public boolean isEnabled(int level){
					return true;
				}
				public void log(int level, String message){
			        LogUtil.sysout(message);
				}
			});
			
			// key 인증방식일경우
			if (null != privateKey && !"".equals(privateKey)) {
				jsch.addIdentity(privateKey);
			}

			// 세션객체 생성
			session = jsch.getSession(connId, connIp, connPort);

			if (!StringUtil.isEmpty(connPw)) {
				//session.setPassword(connPw); // password 설정
			}

			// 세션관련 설정정보 설정
			java.util.Properties config = new java.util.Properties();

			// 호스트 정보 검사하지 않는다.
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.setTimeout(timeout); // 타임아웃 설정
			

			sysout("connectting.. " + connIp);
			session.connect(); // 접속

			channel = session.openChannel("sftp"); // sftp 채널 접속
			channel.connect();
			
			this.sysout("net.dstone.common.utils.FtpUtil.connect(ip:"+ip+", port:"+port+", id:"+id+", pw:"+pw+", privateKey:"+privateKey+") ::: connected !!!");

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		channelSftp = (ChannelSftp) channel;
	}
	
	/**
	 * SFTP 서버 접속 종료
	 */
	public void disconnect() {
		if(channelSftp != null) {
			channelSftp.quit();
		}
		if(channel != null) {
			channel.disconnect();
		}
		if(session != null) {
			session.disconnect();
		}
		this.sysout("net.dstone.common.utils.FtpUtil.disconnect() !!!");
	}

	/**
	 * SFTP 파일업로드
	 * @param localFileFullName
	 * @param remoteDir
	 */
	public boolean uploadFile(String localFileFullName, String remoteDir) throws Exception {
		boolean isSucceeded = false;
    	if(FileUtil.isFileExist(localFileFullName)) {

    		localFileFullName = StringUtil.replace(localFileFullName, "\\", "/");
    		remoteDir = StringUtil.replace(remoteDir, "\\", "/");
    		if( !StringUtil.isEmpty(remoteDir) ) {
    			if( remoteDir.startsWith("/")) {
    				remoteDir = remoteDir.substring(1);
    			}
    			if( remoteDir.endsWith("/")) {
    				remoteDir = remoteDir.substring(0, remoteDir.length()-1);
    			}
    		}
    		String fileName = FileUtil.getFileName(localFileFullName, true);
    		try(InputStream in = Files.newInputStream(Paths.get(localFileFullName))) { 
    			//파일을 가져와서 inputStream에 넣고 저장경로를 찾아 업로드 
    			channelSftp.cd(remoteDir);
    			channelSftp.put(in, fileName);
    			//log.info("sftpFileUpload success.. ");
    			isSucceeded = true;
    			this.sysout("net.dstone.common.utils.FtpUtil.uploadFile() ::: isSucceeded["+isSucceeded+"]");
    		} catch (Exception e) {
    			this.sysout("net.dstone.common.utils.FtpUtil.uploadFile() ::: isSucceeded["+isSucceeded+"]");
    			e.printStackTrace();
    			throw e;
    		}
    	}
		return isSucceeded;
	}
	
	/**
	 * SFTP 서버 파일 다운로드 
	 * @param downloadPath
	 * @param localFilePath
	 */
	public void downloadFile(String localDir, String remoteFileFullName) throws Exception {
		boolean isSucceeded = false;
		byte[] buffer = new byte[1024];
        BufferedInputStream bis = null;
        OutputStream os = null;
        BufferedOutputStream bos = null;
        
        try {
        	remoteFileFullName = StringUtil.replace(remoteFileFullName, "\\", "/");
    		localDir = StringUtil.replace(localDir, "\\", "/");

    		if( !StringUtil.isEmpty(localDir) ) {
    			if( localDir.endsWith("/")) {
    				localDir = localDir.substring(0, localDir.length()-1);
    			}
    		}
    		
            //SFTP 서버 파일 다운로드 경로
            String cdDir = FileUtil.getFilePath(remoteFileFullName);
            //파일명
            String fileName = FileUtil.getFileName(remoteFileFullName, true);
            
            channelSftp.cd(cdDir);

            bis = new BufferedInputStream(channelSftp.get(fileName));

            //파일 다운로드 SFTP 서버 -> 다운로드 서버
            File newFile = new File(localDir + "/" +fileName);
            os = new FileOutputStream(newFile);
            bos = new BufferedOutputStream(os);
            
            int readCount;
            
            while ((readCount = bis.read(buffer)) > 0) {
                bos.write(buffer, 0, readCount);
            }
			isSucceeded = true;
			this.sysout("net.dstone.common.utils.FtpUtil.downloadFile() ::: isSucceeded["+isSucceeded+"]");
        } catch (Exception e) {
			this.sysout("net.dstone.common.utils.FtpUtil.downloadFile() ::: isSucceeded["+isSucceeded+"]");
			e.printStackTrace();
            throw e;
        } finally {
        	try {
				bis.close();
				bos.close();
	            os.close();
			} catch (IOException e) {
				//log.error(e);
				sysout(e);
			}
		}
	}
	
	/**
	 * SFTP 서버 파일 찾기, 파일목록보기
	 * @param remoteFileFullName
	 * @return findFileName
	 * @throws Exception
	 */
	public ArrayList<String> searchFile(String remoteFileFullName) throws Exception {
		ArrayList<String> searchFileList = new ArrayList<String>();
		String findFileName = "";
        try {
            //SFTP 서버 파일 다운로드 경로
        	String cdDir = FileUtil.getFilePath(remoteFileFullName);
            //파일명
            String fileName = FileUtil.getFileName(remoteFileFullName, true);
            
            channelSftp.cd(cdDir);

            Vector<LsEntry> fileList = channelSftp.ls(cdDir);
            
            for(int i=0; i<fileList.size();i++) {
            	LsEntry files = (LsEntry) fileList.get(i);
            	//파일 찾는부분
            	files.getFilename().matches(fileName+(".*"));
            	if(files.getFilename().matches(fileName+(".*"))) {
            		findFileName = files.getFilename();
            		searchFileList.add(fileName);
            	}
            }
        } catch (Exception e) {
        	e.printStackTrace();
            throw e;
		}
        return searchFileList;
	}


	/**
	 * SFTP 파일삭제
	 * 
	 * @param remoteFileFullName
	 * @throws Exception
	 */
	public boolean deleteFile(String remoteFileFullName) throws Exception {
		boolean isSucceeded = false;
		try {
			if (!StringUtil.isEmpty(remoteFileFullName)) {
	            channelSftp.rm(remoteFileFullName);
	            isSucceeded = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.sysout("net.dstone.common.utils.FtpUtil.deleteFile("+remoteFileFullName+") ::: isSucceeded[" + isSucceeded + "]");
		}
		return isSucceeded;
	}

	/**
	 * SFTP 디렉토리삭제
	 * 
	 * @param remoteDirFullName
	 * @throws Exception
	 */
	public boolean deleteDir(String remoteDirFullName) throws Exception {
		boolean isSucceeded = false;
		try {
			if (!StringUtil.isEmpty(remoteDirFullName)) {
				remoteDirFullName = StringUtil.replace(remoteDirFullName, "\\", "/");
	            channelSftp.rmdir(remoteDirFullName);
	            isSucceeded = true;
	            this.sysout("net.dstone.common.utils.FtpUtil.deleteDir("+remoteDirFullName+") ::: isSucceeded[" + isSucceeded + "]");
			}
		} catch (Exception e) {
			this.sysout("net.dstone.common.utils.FtpUtil.deleteDir("+remoteDirFullName+") ::: isSucceeded[" + isSucceeded + "]");
			e.printStackTrace();
		}
		return isSucceeded;
	}

}
