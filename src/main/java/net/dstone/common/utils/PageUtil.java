package net.dstone.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public class PageUtil {

	public static int DEFAULT_PAGE_SIZE = 10;
	
	// (필수사항)
	/**
	 * 페이지당보여줄리스트갯수
	 */
	public int intPageSize = 5;
	/**
	 * 페이지그룹사이즈
	 */
	public int intPageGroupSize = 10;
	// (필수사항)

	/**
	 * 총데이타로의 갯수
	 */
	public int intTotalRow = 0;
	/**
	 * 현재페이지넘버
	 */
	public int intPageNum = 1;
	/**
	 * 검색시작넘버
	 */
	public int intStart = 1;
	/**
	 * 검색끝넘버
	 */
	public int intEnd = 1;
	/**
	 * 총페이지갯수
	 */
	public int intTotalPage = 1;
	/**
	 * 현재페이지에서의로갯수
	 */
	public int intCurrentRow = 0;
	/**
	 * 총페이지그룹갯수
	 */
	public int intTotaltPgGroup = 1;
	/**
	 * 현재페이지그룹넘버
	 */
	public int intPgGroupNum = 1;
	/**
	 * 현재페이지그룹에서 시작할페이지
	 */
	public int intPgStartNum = 1;
	/**
	 * 현재페이지그룹에서 끝나는페이지
	 */
	public int intPgEndNum = 1;
	/**
	 * 이전페이지그룹에서 시작할페이지
	 */
	public int intPrePgStartNum = 1;
	/**
	 * 이전페이지그룹에서 끝날 페이지
	 */
	public int intPrePgEndtNum = 1;
	/**
	 * 이후페이지그룹에서 시작할페이지
	 */
	public int intPostPgStartNum = 1;
	/**
	 * 이후페이지그룹에서 끝날 페이지
	 */
	public int intPostPgEndtNum = 1;

	String IMG_PRE = "/img/blat2.gif";

	String IMG_NEXT = "/img/blat5.gif";

	private void debug(Object o) {
		System.out.println(o);
	}

	/**
	 * PageUtil 생성자 주석.
	 */
	private PageUtil() {
		super();
	}

	/**
	 * PageUtil 생성자 주석.
	 * 
	 * @param pageNum
	 *            현재페이지
	 * @param listsize
	 *            페이지당보여줄리스트갯수
	 * @param totalRowCnt
	 *            총데이타갯수
	 */
	public PageUtil(int pageNum, int listsize, int totalRowCnt) {
		paging(pageNum, listsize, totalRowCnt);
	}

	/**
	 * PageUtil 내의 데이터를 체크하기위한 메소드.
	 */
	public void checkData() {
		debug("-------------------------------------------------------");
		debug("");
		debug("intPageSize      페이지당보여줄리스트갯수          : " + this.intPageSize);
		debug("intPageGroupSize    페이지그룹사이즈                  : " + this.intPageGroupSize);
		debug("intTotalRow      총데이타로의 갯수                 : " + this.intTotalRow);
		debug("intPageNum             현재페이지넘버                   : " + this.intPageNum);
		debug("");
		debug("intTotalPage      총페이지갯수                   : " + this.intTotalPage);
		debug("intStart       검색시작넘버                   : " + this.intStart);
		debug("intEnd        검색끝넘버                   : " + this.intEnd);
		debug("intCurrentRow     현재페이지에서의로갯수            : " + this.intCurrentRow);
		debug("intTotaltPgGroup    총페이지그룹갯수                  : " + this.intTotaltPgGroup);
		debug("intPgGroupNum     현재페이지그룹넘버                : " + this.intPgGroupNum);
		debug("intPgStartNum     현재페이지그룹에서 시작할페이지   : " + this.intPgStartNum);
		debug("intPgEndNum      현재페이지그룹에서 끝나는페이지   : " + this.intPgEndNum);
		debug("intPrePgStartNum    이전페이지그룹에서 시작할페이지   : " + this.intPrePgStartNum);
		debug("intPrePgEndtNum    이전페이지그룹에서 끝날 페이지    : " + this.intPrePgEndtNum);
		debug("intPostPgStartNum   이후페이지그룹에서 시작할페이지   : " + this.intPostPgStartNum);
		debug("intPostPgEndtNum    이후페이지그룹에서 끝날 페이지    : " + this.intPostPgEndtNum);
	}

	/**
	 * @param request
	 * @param formName
	 * @param targetPageParamName
	 * @param scriptCondition
	 * @param sFunctionName
	 * @return
	 */
	public String htmlPostPage(javax.servlet.http.HttpServletRequest request, String formName, String targetPageParamName) {

		StringBuffer html = new StringBuffer("");
		String contxtRoot = request.getContextPath();

		html.append("<table width=100%>\n");
		html.append(" <tr>\n");
		html.append("  <td class='page' height=30 width=100%  align=center>\n");
		if (this.intPrePgStartNum == this.intPgStartNum) {
			html.append("<img src='").append(contxtRoot).append(IMG_PRE).append("'>\n");
		} else {
			html.append("<a href=javascript:").append("goPage").append("('").append(this.intPrePgStartNum).append("'); ><img src='").append(contxtRoot).append(IMG_PRE).append("' border='0' title='이전 10페이지'></a>");
		}
		html.append(" &nbsp;&nbsp;");
		for (int i = this.intPgStartNum; i < this.intPgEndNum + 1; i++) {
			if (i == this.intPageNum) {
				html.append("<B>").append(i).append("</B> ");
			} else {
				html.append("[<a href=javascript:").append("goPage").append("('").append(i).append("'); title='" + i + "페이지 이동'>").append(i).append("</a>] ");
			}
		}
		html.append(" &nbsp;&nbsp;");
		if (this.intPostPgStartNum == this.intPgStartNum) {
			html.append("<img src='").append(contxtRoot).append(IMG_NEXT).append("' width='10' height='9'>\n");
		} else {
			html.append("<a href=javascript:").append("goPage").append("('").append(this.intPostPgStartNum).append("'); ><img src='").append(contxtRoot).append(IMG_NEXT).append("' width='10' height='9' border='0' title='다음 10페이지'></a>\n");
		}
		html.append("  </td>\n");
		html.append(" </tr>\n");
		html.append("</table>\n");

		html.append("<script language='javascript'>\n").append("function goPage(page){\n").append("document.").append(formName).append(".").append(targetPageParamName).append(".value=page;").append("document.").append(formName).append(".").append("submit();\n").append("}\n").append("</script>\n");

		return html.toString();
	}

	/**
	 * @param request
	 * @param formName
	 * @param targetPageParamName
	 * @param scriptCondition
	 * @param sFunctionName
	 * @return
	 */
	public String htmlPostPage(javax.servlet.http.HttpServletRequest request, String formName, String targetPageParamName, String sFunctionName) {

		StringBuffer html = new StringBuffer("");
		String contxtRoot = request.getContextPath();

		html.append("<table width=100%>\n");
		html.append(" <tr>\n");
		html.append("  <td class='page' height=30 width=100%  align=center>\n");
		if (this.intPrePgStartNum == this.intPgStartNum) {
			html.append("<img src='").append(contxtRoot).append(IMG_PRE).append("'>\n");
		} else {
			html.append("<a href=javascript:").append(sFunctionName).append("('").append(this.intPrePgStartNum).append("'); ><img src='").append(contxtRoot).append(IMG_PRE).append("' border='0' title='이전 10페이지'></a>");
		}
		html.append(" &nbsp;&nbsp;");
		for (int i = this.intPgStartNum; i < this.intPgEndNum + 1; i++) {
			if (i == this.intPageNum) {
				html.append("<B>").append(i).append("</B> ");
			} else {
				html.append("[<a href=javascript:").append(sFunctionName).append("('").append(i).append("'); title='" + i + "페이지 이동'>").append(i).append("</a>] ");
			}
		}
		html.append(" &nbsp;&nbsp;");
		if (this.intPostPgStartNum == this.intPgStartNum) {
			html.append("<img src='").append(contxtRoot).append(IMG_NEXT).append("' width='10' height='9'>\n");
		} else {
			html.append("<a href=javascript:").append(sFunctionName).append("('").append(this.intPostPgStartNum).append("'); ><img src='").append(contxtRoot).append(IMG_NEXT).append("' width='10' height='9' border='0' title='다음 10페이지'></a>\n");
		}
		html.append("  </td>\n");
		html.append(" </tr>\n");
		html.append("</table>\n");

		//html.append("<script language='javascript'>\n").append("function goPage(page){\n").append("document.").append(formName).append(".").append(targetPageParamName).append(".value=page;").append("document.").append(formName).append(".").append("submit();\n").append("}\n").append("</script>\n");

		return html.toString();
	}

	public void paging(int pageNum, int listsize, int iTotal) {

		try {
			/***** 디비검색이전 세팅값을 조정1 시작 *****/
			this.intPageNum = ((pageNum == 0) ? 1 : pageNum); // 보고자하는 페이지
			this.intPageSize = listsize; // 페이지당보여줄리스트갯수
			this.intStart = intPageSize * (intPageNum - 1); // 검색시작넘버
			this.intEnd = intPageSize * intPageNum; // 검색끝넘버
			/***** 디비검색이전 세팅값을 조정1 끝 *****/

			/***** 디비검색중 세팅값을 조정2 시작 *****/
			this.intTotalRow = iTotal;
			/***** 디비검색중 세팅값을 조정2 끝 *****/

			/***** 디비검색이후 세팅값을 조정3 시작 *****/
			innerPaging();
			/***** 디비검색이후 세팅값을 조정3 끝 *****/

		} catch (Exception ee) {
			debug("PageUtil paging 메소드 에서 예외발생. 세부내용:" + ee.toString());
		}
	}

	private void innerPaging() {
		// 총페이지갯수
		this.intTotalPage = (((this.intTotalRow % this.intPageSize) == 0) ? (this.intTotalRow / this.intPageSize) : ((this.intTotalRow / this.intPageSize) + 1));
		// 현재페이지에서의로갯수
		if (this.intTotalPage > this.intPageNum) {
			this.intCurrentRow = this.intPageSize;
		} else if (this.intTotalPage == this.intPageNum) {
			this.intCurrentRow = this.intTotalRow - ((this.intTotalPage - 1) * this.intPageSize);
		} else if (this.intTotalPage < this.intPageNum) {
			this.intCurrentRow = 0;
		}
		// 총페이지그룹갯수
		this.intTotaltPgGroup = (((this.intTotalPage % this.intPageGroupSize) == 0) ? (this.intTotalPage / this.intPageGroupSize) : ((this.intTotalPage / this.intPageGroupSize) + 1));
		// 현재페이지그룹넘버
		for (int i = 1; i < this.intTotaltPgGroup + 1; i++) {
			if ((((i - 1) * this.intPageGroupSize) < this.intPageNum) && (this.intPageNum <= (i) * this.intPageGroupSize)) {
				this.intPgGroupNum = i;
			}
		}
		// 정리 1. (검색 scope이 현재의 총데이타범위를 넘어갈때)
		if (this.intStart > this.intTotalRow) {
			this.intStart = (this.intTotalPage - 1) * this.intPageSize + 1;
			this.intEnd = this.intTotalRow;
			this.intPageNum = this.intTotalPage; // 현재페이지넘버
			this.intPgGroupNum = this.intTotaltPgGroup; // 총페이지그룹갯수
		}
		// 현재페이지그룹에서 시작할페이지
		this.intPgStartNum = (this.intPgGroupNum - 1) * this.intPageGroupSize + 1;
		// 현재페이지그룹에서 끝나는페이지
		if ((this.intPgGroupNum * this.intPageGroupSize) >= this.intTotalPage) {
			this.intPgEndNum = this.intTotalPage;
		} else {
			this.intPgEndNum = this.intPgGroupNum * this.intPageGroupSize;
		}
		// 이전페이지그룹에서 시작할페이지
		if (this.intPgGroupNum > 1) {
			this.intPrePgStartNum = this.intPgStartNum - this.intPageGroupSize;
		} else {
			this.intPrePgStartNum = 1;
		}
		// 이전페이지그룹에서 끝날 페이지
		this.intPrePgEndtNum = this.intPrePgStartNum + this.intPageGroupSize - 1;
		if (this.intPrePgEndtNum > this.intTotalPage) {
			this.intPrePgEndtNum = this.intTotalPage;
		}

		// 이후페이지그룹에서 시작할페이지
		if (this.intPgGroupNum < this.intTotaltPgGroup) {
			this.intPostPgStartNum = this.intPgStartNum + this.intPageGroupSize;
		} else if (this.intPgGroupNum == this.intTotaltPgGroup) {
			this.intPostPgStartNum = this.intPgStartNum;
		}
		// 이후페이지그룹에서 끝날 페이지
		this.intPostPgEndtNum = this.intPostPgStartNum + this.intPageGroupSize - 1;
		if (this.intPostPgEndtNum > this.intTotalPage) {
			this.intPostPgEndtNum = this.intTotalPage;
		}

		if (this.intTotalRow < 0) {
			this.intTotalRow = 0;
		}
		if (this.intCurrentRow < 0) {
			this.intCurrentRow = 0;
		}
		if (this.intStart < 0) {
			this.intStart = 1;
		}
		if (this.intEnd < 0) {
			this.intEnd = 1;
		}
		if (this.intPgStartNum <= 0) {
			this.intPgStartNum = 1;
		}
		if (this.intPgEndNum <= 0) {
			this.intPgEndNum = 1;
		}
		if (this.intPrePgStartNum <= 0) {
			this.intPrePgStartNum = 1;
		}
		if (this.intPrePgEndtNum <= 0) {
			this.intPrePgEndtNum = 1;
		}
		if (this.intPostPgStartNum <= 0) {
			this.intPostPgStartNum = 1;
		}
		if (this.intPostPgEndtNum <= 0) {
			this.intPostPgEndtNum = 1;
		}
	}
}
