package net.dstone.common.utils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import net.dstone.common.config.ConfigProperty;

@Component
public class MailUtil {

	@Autowired 
	ConfigProperty configProperty; // 프로퍼티 가져오는 bean

	private JavaMailSender getJavaMailSender() { 
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(configProperty.getProperty("mail.host"));
		mailSender.setPort( Integer.parseInt(configProperty.getProperty("mail.port")) );

		mailSender.setUsername(configProperty.getProperty("mail.user"));
		mailSender.setPassword(configProperty.getProperty("mail.password"));
		
		Properties props = mailSender.getJavaMailProperties();

		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		//props.put("mail.debug", "true");
		
		return mailSender;
	}

	/**
	 * 메일전송 메소드<br>
	 * @param htmlYn HTML형식여부. (false일 경우 Text.)<br>
	 * @param from 송신자이메일<br>
	 * @param to 수신자이메일<br>
	 * @param subject 제목<br> 
	 * @param mailMsg 메일내용<br>
	 * @throws Exception
	 */
	public void sendMail(boolean htmlYn, String from, String to, String subject, String mailMsg) throws Exception {
		this.sendMail(htmlYn, from, to, null, subject, mailMsg, null);
	}

	/**
	 * 메일전송 메소드<br>
	 * @param htmlYn HTML형식여부. (false일 경우 Text.)<br>
	 * @param from 송신자이메일<br>
	 * @param toList 수신자이메일목록<br>
	 * @param ccList cc이메일목록<br>
	 * @param subject 제목<br> 
	 * @param mailMsg 메일내용<br>
	 * @throws Exception
	 */
	public void sendMail(boolean htmlYn, String from, List<String> toList, List<String> ccList, String subject, String mailMsg) throws Exception {
		for(String to : toList) {
			this.sendMail(htmlYn, from, to, ccList, subject, mailMsg, null);
		}
	}
	
	/**
	 * 메일전송 메소드<br>
	 * @param htmlYn HTML형식여부. (false일 경우 Text.)<br>
	 * @param from 송신자이메일<br>
	 * @param to 수신자이메일<br>
	 * @param ccList cc이메일목록<br>
	 * @param subject 제목<br> 
	 * @param mailMsg 메일내용<br>
	 * @param keyVals 치환항목<br>
	 * @throws Exception
	 */
	public void sendMail(boolean htmlYn, String from, String to, List<String> ccList, String subject, String text, Map<String, String> keyVals) throws Exception {
		this.sendMail(htmlYn, from, to, ccList, subject, text, keyVals, null);
	}

	/**
	 * 메일전송 메소드<br>
	 * @param htmlYn HTML형식여부. (false일 경우 Text.)<br>
	 * @param from 송신자이메일<br>
	 * @param to 수신자이메일<br>
	 * @param ccList cc이메일목록<br>
	 * @param subject 제목<br> 
	 * @param mailMsg 메일내용<br>
	 * @param keyVals 치환항목<br>
	 * @param pathToAttachmentList 첨부파일목록<br>
	 * @throws Exception
	 */
	public void sendMail(boolean htmlYn, String from, String to, List<String> ccList, String subject, String mailMsg, Map<String, String> keyVals, List<String> pathToAttachmentList) throws Exception {
		JavaMailSender javaMailSender = getJavaMailSender();
		MimeMessage message = javaMailSender.createMimeMessage();
	    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
		message.setFrom(new InternetAddress(from));
		message.addRecipients(Message.RecipientType.TO, to);
		if( ccList != null ) {
			for(String cc : ccList) {
				message.addRecipients(Message.RecipientType.CC, cc);
			}
		}
	    helper.setSubject(subject);
	    String sendMsg = mailMsg;
	    if( keyVals != null ) {
	    	Iterator<String> keys = keyVals.keySet().iterator();
	    	while(keys.hasNext()) {
	    		String key = keys.next();
	    		String val = keyVals.get(key);
	    		sendMsg = StringUtil.replace(sendMsg, key, val);
	    	}
	    }
	    helper.setText(sendMsg, htmlYn);
	    if( pathToAttachmentList != null && pathToAttachmentList.size()>0 ) {
	    	for(String pathToAttachment : pathToAttachmentList) {
	    	    if( !StringUtil.isEmpty(pathToAttachment) ) {
	    	    	if( FileUtil.isFileExist(pathToAttachment) ) {
	    			    String fileName = FileUtil.getFileName(pathToAttachment, true);
	    			    FileSystemResource file  = new FileSystemResource(new File(pathToAttachment));
	    			    helper.addAttachment(fileName, file);
	    	    	}
	    	    }
	    	}
	    }
		javaMailSender.send(message);
	}

}
