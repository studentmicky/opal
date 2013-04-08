/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.server.httpd;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.mgt.SecurityManager;
import org.eclipse.jetty.ajp.Ajp13SocketConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.FileResource;
import org.obiba.opal.core.cfg.OpalConfigurationExtension;
import org.obiba.opal.core.runtime.NoSuchServiceConfigurationException;
import org.obiba.opal.core.runtime.OpalRuntime;
import org.obiba.opal.core.runtime.Service;
import org.obiba.opal.core.service.SubjectAclService;
import org.obiba.opal.server.httpd.security.AuthenticationFilter;
import org.obiba.opal.server.ssl.SslContextFactory;
import org.obiba.runtime.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractRefreshableConfigApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.RequestContextFilter;

/**
 *
 */
@Component
public class OpalJettyServer implements Service {

  private static final Logger log = LoggerFactory.getLogger(OpalJettyServer.class);

  private static final int MAX_IDLE_TIME = 30000;

  private static final int REQUEST_HEADER_SIZE = 8192;

  private final Server jettyServer;

  private final ServletContextHandler contextHandler;

  private final OpalRuntime opalRuntime;

  private final SubjectAclService subjectAclService;

  @Autowired
  private Version opalVersion;

  private ConfigurableApplicationContext webApplicationContext;

  @Autowired
  @SuppressWarnings({ "unchecked", "PMD.ExcessiveParameterList" })
  public OpalJettyServer(ApplicationContext ctx, SecurityManager securityMgr, final SslContextFactory sslContextFactory,
      PlatformTransactionManager txmgr, OpalRuntime opalRuntime, SubjectAclService subjectAclService,
      @Value("${org.obiba.opal.http.port}") Integer httpPort, @Value("${org.obiba.opal.https.port}") Integer httpsPort,
      @Value("${org.obiba.opal.ajp.port}") Integer ajpPort, @Value("${org.obiba.opal.maxIdleTime}") Integer maxIdleTime

  ) {
    this.opalRuntime = opalRuntime;
    this.subjectAclService = subjectAclService;
    Server server = new Server();
    server.setSendServerVersion(false);
    // OPAL-342: We will manually stop the Jetty server instead of relying its shutdown hook
    server.setStopAtShutdown(false);

    if(httpPort != null && httpPort > 0) {
      SelectChannelConnector httpConnector = new SelectChannelConnector();
      httpConnector.setPort(httpPort);
      httpConnector.setMaxIdleTime(maxIdleTime == null ? MAX_IDLE_TIME : maxIdleTime);
      httpConnector.setRequestHeaderSize(REQUEST_HEADER_SIZE);
      server.addConnector(httpConnector);
    }

    if(httpsPort != null && httpsPort > 0) {

      org.eclipse.jetty.util.ssl.SslContextFactory jettySsl = new org.eclipse.jetty.util.ssl.SslContextFactory() {

        @Override
        protected void doStart() throws Exception {
          setSslContext(sslContextFactory.createSslContext());
        }

        @Override
        public void checkKeyStore() {
        }
      };

      jettySsl.setWantClientAuth(true);
      jettySsl.setNeedClientAuth(false);

      SslSelectChannelConnector sslConnector = new SslSelectChannelConnector(jettySsl);
      sslConnector.setPort(httpsPort);
      sslConnector.setMaxIdleTime(maxIdleTime == null ? MAX_IDLE_TIME : maxIdleTime);
      sslConnector.setRequestHeaderSize(REQUEST_HEADER_SIZE);

      server.addConnector(sslConnector);
    }

    if(ajpPort != null && ajpPort > 0) {
      Connector ajp = new Ajp13SocketConnector();
      ajp.setPort(ajpPort);
      server.addConnector(ajp);
    }

    HandlerList handlers = new HandlerList();

    // Add a file handler that points to the Opal GWT client directory
    handlers.addHandler(createFileHandler("/webapp"));
    // Add a file handler that points to the Opal BIRT extension update-site
    handlers.addHandler(createFileHandler("/update-site"));

    handlers.addHandler(contextHandler = createServletHandler(ctx, txmgr, securityMgr));
    server.setHandler(handlers);

    jettyServer = server;
  }

  @Bean
  public ServletContextHandler getServletContextHandler() {
    return contextHandler;
  }

  @Override
  public boolean isRunning() {
    return jettyServer.isRunning();
  }

  @Override
  public void start() {
    try {
      webApplicationContext.refresh();
      log.info("Starting Opal HTTP/s Server on port {}", jettyServer.getConnectors()[0].getPort());
      jettyServer.start();
    } catch(Exception e) {
      log.error("Error starting jetty", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    try {
      if(webApplicationContext.isActive()) {
        webApplicationContext.close();
      }
    } catch(RuntimeException e) {
      // log and ignore
      log.warn("Exception during web application context shutdown", e);
    }

    try {
      jettyServer.stop();
    } catch(Exception e) {
      // log and ignore
      log.warn("Exception during HTTPd server shutdown", e);
    }

  }

  @Override
  public String getName() {
    return "jetty";
  }

  @Override
  public OpalConfigurationExtension getConfig() throws NoSuchServiceConfigurationException {
    throw new NoSuchServiceConfigurationException(getName());
  }

  private ServletContextHandler createServletHandler(ApplicationContext ctx, PlatformTransactionManager txmgr,
      SecurityManager securityMgr) {
    ServletContextHandler contextHandler = new ServletContextHandler(
        ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
    contextHandler.setContextPath("/");
    contextHandler.addFilter(new FilterHolder(new OpalVersionFilter()), "/*", FilterMapping.DEFAULT);
    contextHandler
        .addFilter(new FilterHolder(new AuthenticationFilter(securityMgr, opalRuntime, subjectAclService)), "/ws/*",
            FilterMapping.DEFAULT);
    // contextHandler.addFilter(new FilterHolder(new X509CertificateAuthenticationFilter()), "/ws/*", FilterMapping.DEFAULT);
    // contextHandler.addFilter(new FilterHolder(new CrossOriginFilter()), "/*", FilterMapping.DEFAULT);
    contextHandler.addFilter(new FilterHolder(new RequestContextFilter()), "/*", FilterMapping.DEFAULT);
    contextHandler.addFilter(new FilterHolder(new TransactionFilter(txmgr)), "/*", FilterMapping.DEFAULT);

    webApplicationContext = new XmlWebApplicationContext();
    webApplicationContext.setParent(ctx);
    ((ConfigurableWebApplicationContext) webApplicationContext).setServletContext(contextHandler.getServletContext());
    ((AbstractRefreshableConfigApplicationContext) webApplicationContext)
        .setConfigLocation("classpath:/META-INF/spring/opal-httpd/context.xml");
    contextHandler.getServletContext()
        .setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);

    return contextHandler;
  }

  private Handler createFileHandler(String directory) {
    String fileUrl = "file://" + System.getProperty("OPAL_DIST") + directory;
    ResourceHandler resourceHandler = new ResourceHandler();
    try {
      resourceHandler.setBaseResource(new FileResource(new URL(fileUrl)));
      resourceHandler.setAliases(true);
      log.info("Created a file handler for the following URL : {}", fileUrl);
    } catch(MalformedURLException e) {
      throw new RuntimeException(e);
    } catch(IOException e) {
      throw new RuntimeException(e);
    } catch(URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return resourceHandler;
  }

  public class OpalVersionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      try {
        if(opalVersion != null) {
          response.addHeader("X-Opal-Version", opalVersion.toString());
        }
      } catch(RuntimeException ignored) {
      }

      filterChain.doFilter(request, response);
    }

  }

  public static class TransactionFilter extends OncePerRequestFilter {

    private final PlatformTransactionManager txManager;

    public TransactionFilter(PlatformTransactionManager txManager) {
      this.txManager = txManager;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
        final FilterChain filterChain) throws ServletException, IOException {
      new TransactionTemplate(txManager).execute(new TransactionCallbackWithoutResult() {
        @Override
        protected void doInTransactionWithoutResult(TransactionStatus status) {
          try {
            filterChain.doFilter(request, response);
          } catch(IOException e) {
            throw new RuntimeException(e);
          } catch(ServletException e) {
            throw new RuntimeException(e);
          }
        }
      });

    }

  }

}
