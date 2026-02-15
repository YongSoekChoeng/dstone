package net.dstone.boot.common.tools.analyzer.consts;

/**
 * 기능종류(UI:화면/JS:자바스크립트/CT:컨트롤러/SV:서비스/DA:DAO/OT:나머지)
 * @author jysn007
 */
public enum ClzzKind {
	UI			("UI", "화면")
	, JS		("JS", "자바스크립트")
	, CT		("CT", "컨트롤러")
	, SV		("SV", "서비스")
	, DA		("DA", "DAO")
	, OT		("OT", "나머지")
	;

	private String clzzKindCd;
	private String clzzKindNm;

	private ClzzKind(String clzzKindCd, String clzzKindNm) {
		this.clzzKindCd = clzzKindCd;
		this.clzzKindNm = clzzKindNm;
	}

	public String getClzzKindCd() {
		return clzzKindCd;
	}


	public void setClzzKindCd(String clzzKindCd) {
		this.clzzKindCd = clzzKindCd;
	}


	public String getClzzKindNm() {
		return clzzKindNm;
	}


	public void setClzzKindNm(String clzzKindNm) {
		this.clzzKindNm = clzzKindNm;
	}

	public static ClzzKind getClzzKindCd(String clzzKindCd) {
		ClzzKind clzzKindSel = null;
		for(ClzzKind clzzKind : ClzzKind.values()) {
			if(clzzKind.getClzzKindCd().equals(clzzKindCd)) {
				clzzKindSel = clzzKind;
				break;
			}
		}
		return clzzKindSel;
	}


}
