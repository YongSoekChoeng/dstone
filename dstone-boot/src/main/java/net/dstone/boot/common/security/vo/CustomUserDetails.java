package net.dstone.boot.common.security.vo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {

    private static final long serialVersionUID = -4450269958885980297L;
    private String username;
    private String password;
    private List<GrantedAuthority> authorities;
	private Map<String, String> authResultMap;

    public CustomUserDetails(String userName, String password)
    {
        this.username = userName;
        this.password = password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    public void setAuthorities(List<GrantedAuthority> authorities) {
    	this.authorities = authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

	public Map<String, String> getAuthResultMap() {
		return authResultMap;
	}

	public void setAuthResultMap(Map<String, String> authResultMap) {
		this.authResultMap = authResultMap;
	}

	@Override
	public String toString() {
		return "CustomUserDetails [username=" + username + ", password=" + password + ", authorities=" + authorities
				+ ", authResultMap=" + authResultMap + "]";
	}
 }
