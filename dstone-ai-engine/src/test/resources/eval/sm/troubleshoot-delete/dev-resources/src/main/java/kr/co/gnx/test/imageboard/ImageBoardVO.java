package kr.co.gnx.test.imageboard;

import kr.co.gnx.base.BaseVO;

/**
 * 이미지 게시판 VO
 * seq / mb_id / use_yn / in_emp_cd / in_dtm / up_emp_cd / up_dtm / search_word / json_string 은 BaseVO에 있다.
 */
public class ImageBoardVO extends BaseVO {

	private String title;		/* 제목 */
	private String writer_nm;	/* 등록자명 */
	private String image_url;	/* 이미지 URL */
	private String thumb_url;	/* 썸네일 URL */

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getWriter_nm() {
		return writer_nm;
	}
	public void setWriter_nm(String writer_nm) {
		this.writer_nm = writer_nm;
	}
	public String getImage_url() {
		return image_url;
	}
	public void setImage_url(String image_url) {
		this.image_url = image_url;
	}
	public String getThumb_url() {
		return thumb_url;
	}
	public void setThumb_url(String thumb_url) {
		this.thumb_url = thumb_url;
	}
}
