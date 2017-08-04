package se.skltpservices.tools;

import static org.junit.Assert.*

import java.security.Permission

import org.junit.Before
import org.junit.Test

class VirtualiseringGeneratorTest {
	VirtualiseringGenerator virtGen

	protected static class ExitException extends SecurityException {
		public final int exitStatus;
		public ExitException(int exitStatus)
		{
			super("System.exit")
			this.exitStatus = exitStatus
		}
	}

	private static class CatchExitSecurityManager extends SecurityManager {
		@Override
		public void checkPermission(Permission perm) {
		}

		@Override
		public void checkPermission(Permission perm, Object context) {
		}

		@Override
		public void checkExit(int exitStatus) {
			super.checkExit exitStatus
			throw new ExitException(exitStatus)
		}
	}

	public VirtualiseringGeneratorTest() {
		System.setSecurityManager(new CatchExitSecurityManager())
		System.setProperty "groovy.grape.enable", "false"
	}

	@Before
	public void init() {
		virtGen = new VirtualiseringGenerator()
	}

	@Test
	public void testCheckEmpty() {
		def dirs = [ new File("src/test/testdata/empty") ]
		try {
			virtGen.checkDirectoriesAndFiles dirs
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}

	@Test
	public void testCheckNoWsdl() {
		def dirs = [ new File("src/test/testdata/noWsdl") ]
		try {
			virtGen.checkDirectoriesAndFiles dirs
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}

	@Test
	public void testCheckMultipleWsdl() {
		def dirs = [ new File("src/test/testdata/multipleWsdl") ]
		try {
			virtGen.checkDirectoriesAndFiles dirs
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}

	@Test
	public void testCheckMultipleXsd() {
		def dirs = [ new File("src/test/testdata/multipleXsd") ]
		try {
			virtGen.checkDirectoriesAndFiles dirs
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}

	@Test
	public void testCheckMultiDigitNamespaceVersion() {
		def dirs = [ new File("src/test/testdata/multiDigitNamespaceVersion") ]
		try {
			virtGen.checkDirectoriesAndFiles dirs
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}

	@Test
	public void testBuildFail() {
		def dirs = [ new File("src/test/testdata/buildFail/schemas/interactions/a") ]
		def targetDir = new File("/tmp")
		try {
			virtGen.buildVirtualServices dirs, targetDir, "1", "shortname"
			fail "Didn't exit"
		} catch (ExitException e) {
			assertNotEquals 0, e.exitStatus
		}
	}
}
