/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.initializer;

import java.io.IOException;
import java.util.logging.LogManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.SLF4JLogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.OutputCapture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.java.JavaLoggingSystem;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.PropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link LoggingApplicationContextInitializer}.
 * 
 * @author Dave Syer
 * @author Phillip Webb
 */
public class LoggingApplicationContextInitializerTests {

	private static final String[] NO_ARGS = {};

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	private LoggingApplicationContextInitializer initializer = new LoggingApplicationContextInitializer();

	private Log logger = new SLF4JLogFactory().getInstance(getClass());

	private SpringApplication springApplication = new SpringApplication();

	private GenericApplicationContext context = new GenericApplicationContext();

	@Before
	public void init() throws SecurityException, IOException {
		LogManager.getLogManager().readConfiguration(
				JavaLoggingSystem.class.getResourceAsStream("logging.properties"));
		this.initializer.initialize(new SpringApplication(), NO_ARGS);
	}

	@After
	public void clear() {
		System.clearProperty("LOG_FILE");
		System.clearProperty("LOG_PATH");
		System.clearProperty("PID");
	}

	@Test
	public void testDefaultConfigLocation() {
		GenericApplicationContext context = new GenericApplicationContext();
		this.initializer.initialize(context);
		this.logger.info("Hello world");
		String output = this.outputCapture.toString().trim();
		assertTrue("Wrong output:\n" + output, output.contains("Hello world"));
		assertFalse("Wrong output:\n" + output, output.contains("???"));
	}

	@Test
	public void testOverrideConfigLocation() {
		this.context.getEnvironment().getPropertySources()
				.addFirst(new PropertySource<String>("manual") {
					@Override
					public Object getProperty(String name) {
						if ("logging.config".equals(name)) {
							return "classpath:logback-nondefault.xml";
						}
						return null;
					}
				});
		this.initializer.initialize(this.context);
		this.logger.info("Hello world");
		String output = this.outputCapture.toString().trim();
		assertTrue("Wrong output:\n" + output, output.contains("Hello world"));
		assertFalse("Wrong output:\n" + output, output.contains("???"));
		assertTrue("Wrong output:\n" + output, output.startsWith("/tmp/spring.log"));
	}

	@Test
	public void testOverrideConfigDoesNotExist() throws Exception {
		this.context.getEnvironment().getPropertySources()
				.addFirst(new PropertySource<String>("manual") {
					@Override
					public Object getProperty(String name) {
						if ("logging.config".equals(name)) {
							return "doesnotexist.xml";
						}
						return null;
					}
				});
		this.initializer.initialize(this.context);
		// Should not throw
	}

	@Test
	public void testAddLogFileProperty() {
		this.context.getEnvironment().getPropertySources()
				.addFirst(new PropertySource<String>("manual") {
					@Override
					public Object getProperty(String name) {
						if ("logging.config".equals(name)) {
							return "classpath:logback-nondefault.xml";
						}
						if ("logging.file".equals(name)) {
							return "foo.log";
						}
						return null;
					}
				});
		this.initializer.initialize(this.context);
		Log logger = LogFactory.getLog(LoggingApplicationContextInitializerTests.class);
		logger.info("Hello world");
		String output = this.outputCapture.toString().trim();
		assertTrue("Wrong output:\n" + output, output.startsWith("foo.log"));
	}

	@Test
	public void testAddLogPathProperty() {
		this.context.getEnvironment().getPropertySources()
				.addFirst(new PropertySource<String>("manual") {
					@Override
					public Object getProperty(String name) {
						if ("logging.config".equals(name)) {
							return "classpath:logback-nondefault.xml";
						}
						if ("logging.path".equals(name)) {
							return "foo/";
						}
						return null;
					}
				});
		this.initializer.initialize(this.context);
		Log logger = LogFactory.getLog(LoggingApplicationContextInitializerTests.class);
		logger.info("Hello world");
		String output = this.outputCapture.toString().trim();
		assertTrue("Wrong output:\n" + output, output.startsWith("foo/spring.log"));
	}

	@Test
	public void parseDebugArg() throws Exception {
		this.initializer.initialize(this.springApplication, new String[] { "--debug" });
		this.initializer.initialize(this.context);
		this.logger.debug("testatdebug");
		this.logger.trace("testattrace");
		assertThat(this.outputCapture.toString(), containsString("testatdebug"));
		assertThat(this.outputCapture.toString(), not(containsString("testattrace")));
	}

	@Test
	public void parseTraceArg() throws Exception {
		this.context = new GenericApplicationContext();
		this.initializer.initialize(this.springApplication, new String[] { "--trace" });
		this.initializer.initialize(this.context);
		this.logger.debug("testatdebug");
		this.logger.trace("testattrace");
		assertThat(this.outputCapture.toString(), containsString("testatdebug"));
		assertThat(this.outputCapture.toString(), containsString("testattrace"));
	}

	@Test
	public void parseArgsDisabled() throws Exception {
		this.initializer.setParseArgs(false);
		this.initializer.initialize(this.springApplication, new String[] { "--debug" });
		this.initializer.initialize(this.context);
		this.logger.debug("testatdebug");
		assertThat(this.outputCapture.toString(), not(containsString("testatdebug")));
	}

	@Test
	public void parseArgsDoesntReplace() throws Exception {
		this.initializer.setSpringBootLogging(LogLevel.ERROR);
		this.initializer.setParseArgs(false);
		this.initializer.initialize(this.springApplication, new String[] { "--debug" });
		this.initializer.initialize(this.context);
		this.logger.debug("testatdebug");
		assertThat(this.outputCapture.toString(), not(containsString("testatdebug")));
	}
}
