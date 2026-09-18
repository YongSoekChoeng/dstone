package net.dstone.common.exception;

import net.dstone.common.consts.ErrCd;

@SuppressWarnings("serial")
public class BizException extends Exception{
	private ErrCd errCd;
	private String errDetailMsg;
	public BizException(ErrCd errCd) {
		super(errCd.getErrMsg());
		this.errCd = errCd;
	}
	public BizException(ErrCd errCd, String errDetailMsg) {
		super(errCd.getErrMsg());
		this.errCd = errCd;
		this.errDetailMsg = errDetailMsg;
	}
	public ErrCd getErrCd() {
		return errCd;
	}
	public void setErrCd(ErrCd errCd) {
		this.errCd = errCd;
	}
	public String getErrDetailMsg() {
		return errDetailMsg;
	}
	public void setErrDetailMsg(String errDetailMsg) {
		this.errDetailMsg = errDetailMsg;
	}
}
