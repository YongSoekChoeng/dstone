package net.dstone.common.consts;

public enum ErrCd {
	
	/*************************************************************************
	 * 에러코드 구성
	 * 'E'(에러)/'W'(경고)/'I'(정보) + 'XXX'(업무명) + '0000'(시퀀스)
	 *************************************************************************/
	SYS_ERR				("ESYS0001", "시스템에러 입니다.")
	
	, USER_NOT_REG		("ECOM0001", "등록되지 않은 사용자 입니다.")
	, WRONG_PASSWD		("ECOM0002", "패스워드가 동일하지 않습니다.")
	, ACCESS_DENIED		("ECOM0003", "접근권한이 없습니다.")
	, MUST_LOGIN		("ECOM0004", "로그인을 하셔야 합니다.")
	, ALREADY_LOGIN		("ECOM0005", "이미 로그인이 되어있습니다.")
	, LOGIN_FAIL		("ECOM0006", "로그인수행시 예외가 발생했습니다.")
	;

	private String errCd;
	private String errMsg;

	private ErrCd(String cd, String msg) {
		this.errCd = cd;
		this.errMsg = msg;
	}

	public String getErrCd() {
		return errCd;
	}

	public void setErrCd(String cd) {
		this.errCd = cd;
	}

	public String getErrMsg() {
		return errMsg;
	}

	public void setErrMsg(String msg) {
		this.errMsg = msg;
	}

	public static ErrCd getErrCdByCd(String cd) {
		ErrCd errCdSel = null;
		for(ErrCd errCd : ErrCd.values()) {
			if(errCd.getErrCd().equals(cd)) {
				errCdSel = errCd;
				break;
			}
		}
		return errCdSel;
	}
	
	public static String getMsgByCd(String cd) {
		String msg = "";
		for(ErrCd errCd : ErrCd.values()) {
			if(errCd.getErrCd().equals(cd)) {
				msg = errCd.getErrMsg();
				break;
			}
		}
		return msg;
	}
}
