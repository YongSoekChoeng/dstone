package kr.co.gnx.sample.imageboard;

import kr.co.gnx.base.BaseVO;

/**
 * 이미지게시판 VO
 * seq / mb_id / use_yn / in_emp_cd / in_dtm / up_emp_cd / up_dtm / search_word / json_string 은 BaseVO에 있다.
 */
public class ImageBoardVO extends BaseVO {

	private String title;		/* 제목 */
	private String file_no;		/* 파일번호 */
	private String file_url;	/* 파일URL */
	private String writer_nm;	/* 등록자명 */

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getFile_no() {
		return file_no;
	}
	public void setFile_no(String file_no) {
		this.file_no = file_no;
	}
	public String getFile_url() {
		return file_url;
	}
	public void setFile_url(String file_url) {
		this.file_url = file_url;
	}
	public String getWriter_nm() {
		return writer_nm;
	}
	public void setWriter_nm(String writer_nm) {
		this.writer_nm = writer_nm;
	}
}
