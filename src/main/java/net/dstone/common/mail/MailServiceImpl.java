package net.dstone.common.mail;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;


@Service
public class MailServiceImpl implements MailService {

	@Autowired
	private JavaMailSender mailSender;
	
	@Autowired
	private MessageSource messageSource;
	
	@Autowired
	private VelocityEngine velocityEngine;

	/**
	 * PLAIN TEXT 로 CC 까지 포함된 함수	
	 */
	@Override
	public void sendMail(String subject, String text, String fromUser,
			String toUser, String[] toCC) {
		// TODO Auto-generated method stub
		MimeMessage message = mailSender.createMimeMessage();
		
		try {
			MimeMessageHelper messageHelper = new MimeMessageHelper(message, true, "UTF-8");
			messageHelper.setSubject(subject);
			messageHelper.setTo(toUser);
			if(toCC != null)
				messageHelper.setCc(toCC);
			messageHelper.setFrom(fromUser);
			messageHelper.setText(text,true);
			mailSender.send(message);
		}catch(MessagingException e ) {
			e.printStackTrace();
		}
	}

	@Override
	public void sendMailHTML(String subject, String url, String fromUser, String toUser, String[] toCC, String headHtml, String tailHTML) {
		// TODO Auto-generated method stub
		String html = "";
		String retHtml = "";
		try {
			html = getHTMLbyUrl(url);
			Document doc = Jsoup.parse(html);
			
			//Elements content = doc.getElementsByClass("container");
			Elements content = doc.getElementsByClass("type1");
			//Elements content = doc.getElementById("contents");
			//Element content = doc.getElementById("contents");
			
			retHtml += "<HTML><head>\n";
			retHtml += getCSS();
			retHtml += "</head><body>";

			for(Element elem : content) {
				retHtml += elem.html();
			}
			//retHtml += content.html();
			retHtml += "</body></html>";
			System.out.println(retHtml);
			//retHtml = retHtml + content.html();
			
			sendMail(subject,retHtml,fromUser,toUser,toCC);
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private String getHTMLbyUrl(String strURL) throws IOException {
		BufferedInputStream in = null;
		StringBuffer sb = new StringBuffer();
		
		URL url = new URL(strURL);
		URLConnection urlConnection = url.openConnection();
		in = new BufferedInputStream(urlConnection.getInputStream());
		
		byte[] bufRead = new byte[4096];
		int lenRead = 0;
		while ((lenRead = in.read(bufRead)) > 0 ) {
			sb.append(new String(bufRead, 0, lenRead,"UTF-8"));
		}
		return sb.toString();
	}
	
	private String getCSS()  {
		StringBuffer sb = new StringBuffer();
		sb.append("<style type='text/css'>																																																			").append("\n");
		sb.append("<!--                                                                                                                        ").append("\n");
		sb.append(".type1 .list{                                                                                                               ").append("\n");
		sb.append("	width:100%;                                                                                                               ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append(".table{                                                                                                               ").append("\n");
		sb.append("	width:920px;                                                                                                              ").append("\n");
		sb.append("	border-collapse: collapse;                                                                                                ").append("\n");
		sb.append("	margin:0 auto;                                                                                                            ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append(".search_sub{                                                                                                                ").append("\n");
		sb.append("	display: none;                                                                                                            ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append(".th{                                                                                                                  ").append("\n");
		sb.append("	background-color: #f9f9f9;                                                                                                ").append("\n");
		sb.append("	color:#000;                                                                                                               ").append("\n");
		sb.append("	font-weight:bold;                                                                                                         ").append("\n");
		sb.append("	height:40px;                                                                                                              ").append("\n");
		sb.append("	border-bottom: #e4e3e2 1px solid;                                                                                         ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append(".td{                                                                                                                  ").append("\n");
		sb.append("	border-bottom: #e4e3e2 1px solid;                                                                                         ").append("\n");
		sb.append("	padding: 8px 0 8px 10px;                                                                                                  ").append("\n");
		sb.append("	vertical-align: middle;                                                                                                   ").append("\n");
		sb.append("	line-height: 120%;                                                                                                        ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append("..top{                                                                                                                ").append("\n");
		sb.append("	border-top: 1px solid #c3c1c1;                                                                                            ").append("\n");
		sb.append("}                                                                                                                           ").append("\n");
		sb.append("//-->                                                                                                                       ").append("\n");
		sb.append("</style>                                                                                                                    ").append("\n");
		return sb.toString();
	}

	@Override
	public void sendMailTemplate(Map<String, Object> param) {
		// TODO Auto-generated method stub
		String fromUser = "";	//보내는 사람
		String toUser = "";		//받는 사람
		String subject = "";		//제목
		String template = "";	//템플릿위치
		String[] toCC = null;	//참조
		
		fromUser = (String)param.get("fromUser");
		toUser = (String)param.get("toUser");
		subject = (String)param.get("subject");
		template = (String)param.get("template");
		toCC = (String[])param.get("toCC");
		
		//String contents = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "UTF-8", template,param); 
		String contents = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, template,param);
	
System.out.println("============================================= 메일전송 시작 =============================================");		
System.out.print("fromUser["+fromUser+"] toUser["+toUser+"] subject["+subject+"]");	
if(toCC != null){
	for(int i=0; i<toCC.length; i++){
		System.out.print("toCC["+toCC[i]+"]");	
	}
}
System.out.println("");	
System.out.println("============================================= 메일전송 끝 =============================================");	

		sendMail(subject, contents, fromUser,toUser,toCC);
	}
	
	
}
