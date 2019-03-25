package net.dstone.common.mail;

import java.util.Map;

public interface MailService {
	
	public void sendMail(String subject, String text, String fromUser, String toUser, String[] toCC);
	
	public void sendMailHTML(String subject,String url, String fromUser, String toUser, String[] toCC, String headHtml, String tailHTML);
	
	public void sendMailTemplate(Map<String, Object> param);
	
}
