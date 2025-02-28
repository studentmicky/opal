/*
 * Copyright (c) 2021 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.opal.web.security;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.session.InvalidSessionException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.obiba.opal.web.model.Opal;
import org.obiba.opal.web.ws.security.NoAuthorization;
import org.obiba.opal.web.ws.security.NotAuthenticated;
import org.obiba.shiro.web.filter.AuthenticationExecutor;
import org.obiba.shiro.web.filter.UserBannedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Path("/auth")
public class AuthenticationResource extends AbstractSecurityComponent {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationResource.class);

  private static final String OBIBA_ID_COOKIE_NAME = "obibaid";

  @Autowired
  private AuthenticationExecutor authenticationExecutor;

  @POST
  @Path("/sessions")
  @NotAuthenticated
  public Response createSession(@SuppressWarnings("TypeMayBeWeakened") @Context HttpServletRequest servletRequest,
      @FormParam("username") String username, @FormParam("password") String password) {
    try {
      //if (SecurityUtils.getSubject().isAuthenticated()) return Response.status(Status.BAD_REQUEST).build();
      authenticationExecutor.login(new UsernamePasswordToken(username, password));
      String sessionId = SecurityUtils.getSubject().getSession().getId().toString();
      log.info("Successful session creation for user '{}' at ip: '{}': session ID is '{}'.", username,
              servletRequest.getRemoteAddr(), sessionId);
      return Response.created(
              UriBuilder.fromPath("/").path(AuthenticationResource.class).path(AuthenticationResource.class, "checkSession")
                      .build(sessionId)).build();
    } catch (UserBannedException e) {
      log.info("Authentication failure: {}", e.getMessage());
      throw e;
    } catch(AuthenticationException e) {
      log.info("Authentication failure of user '{}' at ip: '{}': {}", username, servletRequest.getRemoteAddr(),
          e.getMessage());
      // When a request contains credentials and they are invalid, the a 403 (Forbidden) should be returned.
      return Response.status(Status.FORBIDDEN).build();
    }
  }

  @HEAD
  @Path("/session/{id}")
  public Response checkSession(@PathParam("id") String sessionId) {
    // Find the Shiro Session
    return isValidSessionId(sessionId) ? Response.ok().build() : Response.status(Status.NOT_FOUND).build();
  }

  @DELETE
  @Path("/session/{id}")
  @NoAuthorization
  public Response deleteSession(@PathParam("id") String sessionId) {
    // legacy
    return deleteCurrentSession();
  }

  @DELETE
  @Path("/session/_current")
  @NoAuthorization
  public Response deleteCurrentSession() {
    // Delete the Shiro session
    try {
      Session session = SecurityUtils.getSubject().getSession();
      Object cookieValue = session.getAttribute(HttpHeaders.SET_COOKIE);
      SecurityUtils.getSubject().logout();

      if(cookieValue != null) {
        NewCookie cookie = NewCookie.valueOf(cookieValue.toString());
        if (OBIBA_ID_COOKIE_NAME.equals(cookie.getName())) {
          return Response.ok().header(HttpHeaders.SET_COOKIE,
              new NewCookie(OBIBA_ID_COOKIE_NAME, null, "/", cookie.getDomain(), "Obiba session deleted", 0, true, true)).build();
        }
      }
    } catch(InvalidSessionException e) {
      // Ignore
    }
    return Response.ok().build();
  }

  @GET
  @Path("/session/_current/username")
  @NoAuthorization
  public Opal.Subject getCurrentSubject() {
    // Find the Shiro username
    Subject subject = SecurityUtils.getSubject();
    String principal = subject.getPrincipal() == null ? "" : subject.getPrincipal().toString();
    return Opal.Subject.newBuilder() //
        .setPrincipal(principal) //
        .setType(Opal.Subject.SubjectType.USER) //
        .build();
  }

  @GET
  @Path("/session/{id}/username")
  public Opal.Subject getSubject(@PathParam("id") String sessionId) {
    // Find the Shiro username
    String principal = isValidSessionId(sessionId) ? SecurityUtils.getSubject().getPrincipal().toString() : null;
    return Opal.Subject.newBuilder() //
        .setPrincipal(principal) //
        .setType(Opal.Subject.SubjectType.USER) //
        .build();
  }

}
