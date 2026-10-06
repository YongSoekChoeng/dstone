package net.dstone.common.exception;

import org.springframework.security.core.AuthenticationException;

import net.dstone.common.consts.ErrCd;

@SuppressWarnings("serial")
public class SecException extends AuthenticationException {
	private ErrCd errCd;
	private String errDetailMsg;
	public SecException(ErrCd errCd) {
		super(errCd.getErrMsg());
		this.errCd = errCd;
	}
	public SecException(ErrCd errCd, String errDetailMsg) {
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
