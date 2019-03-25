package net.dstone.common.utils;

import org.springframework.stereotype.Controller;

@Controller
public class InitServlet extends javax.servlet.http.HttpServlet {

	public void init(javax.servlet.ServletConfig config) throws javax.servlet.ServletException {
		super.init(config);
		try {
			/*** 1. net.dstone.common.utils.PropUtil Start ***/
			String rootDirectory = config.getServletContext().getRealPath("/WEB-INF/classes/conf/properties");
			net.dstone.common.utils.PropUtil.initialize(rootDirectory);

		} catch (Exception es) {
			es.printStackTrace();
		}
	}
}
