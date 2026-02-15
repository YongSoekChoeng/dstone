package net.dstone.common.utils;

import org.springframework.stereotype.Controller;

@Controller
public class InitServlet extends jakarta.servlet.http.HttpServlet {

	public void init(jakarta.servlet.ServletConfig config) throws jakarta.servlet.ServletException {
		super.init(config);
		try {
			/*** 1. net.dstone.common.utils.PropUtil Start ***/
			LogUtil.sysout("/*** 1. net.dstone.common.utils.PropUtil Start ***/");
			String rootDirectory = config.getServletContext().getRealPath("/WEB-INF/classes/conf/properties");
			net.dstone.common.utils.PropUtil.initialize(rootDirectory);

		} catch (Exception es) {
			es.printStackTrace();
		}
	}
}
