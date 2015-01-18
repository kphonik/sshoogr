/*
* Copyright (C) 2011-2014 Aestas/IT
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.aestasit.infrastructure.ssh

import com.aestasit.infrastructure.ssh.dsl.SshDslEngine
import com.aestasit.infrastructure.ssh.log.SysOutLogger
import org.junit.BeforeClass
import org.junit.Test

import java.util.concurrent.TimeoutException

/**
 * SSH DSL test case that verifies different DSL syntax use cases.
 *
 * @author Andrey Adamovich
 *
 */
class SshDslTest extends BaseSshTest {

  static SshOptions options
  static SshDslEngine engine

  @BeforeClass
  def static void defineOptions() {
    options = new SshOptions()
    options.with {

      logger = new SysOutLogger()

      defaultHost = '127.0.0.1'
      defaultUser = 'user1'
      defaultPassword = '123456'
      defaultPort = 2233

      reuseConnection = true
      trustUnknownHosts = true

      verbose = true

      execOptions.with {
        showOutput = true
        failOnError = false
        succeedOnExitStatus = 0
        maxWait = 30000
        usePty = true
      }

    }
    engine = new SshDslEngine(options)
  }

  @Test
  void testDefaultSettings() throws Exception {
    engine.remoteSession {
      exec 'whoami'
      exec 'du -s'
      exec 'rm -rf /tmp/test.file'
      scp testFile, '/tmp/test.file'
    }
  }

  @Test
  void testUrlAndOverriding() throws Exception {
    // Test overriding default connection settings through URL.
    engine.remoteSession {

      url = 'user2:654321@localhost:2233'

      exec 'whoami'
      exec 'du -s'
      exec 'rm -rf /tmp/test.file'
      scp testFile, '/tmp/test.file'

    }
  }

  @Test
  void testPasswordlessLogin() throws Exception {
    engine.remoteSession {
      url = 'user2@localhost:2233'
      keyFile = getTestKey()
      exec 'whoami'
    }
  }

  @Test
  void testMethodOverriding() throws Exception {
    // Test overriding default connection settings through method parameter.
    engine.remoteSession('user2:654321@localhost:2233') {

      exec 'whoami'
      exec 'du -s'
      exec 'rm -rf /tmp/test.file'
      scp testFile, '/tmp/test.file'

    }
  }

  @Test
  void testPropertyOverriding() throws Exception {
    // Test overriding default connection settings through delegate parameters.
    engine.remoteSession {

      host = 'localhost'
      user = 'user2'
      password = '654321'
      port = 2233

      exec 'whoami'
      exec 'du -s'
      exec 'rm -rf /tmp/test.file'
      scp testFile, '/tmp/test.file'

    }
  }

  @Test
  void testOutputScripting() throws Exception {
    // Test saving the output and setting exec parameters through a builder.
    engine.remoteSession {
      println ">>>>> COMMAND: whoami"
      def output = exec(command: 'whoami', showOutput: false)
      output.output.eachLine { line -> println ">>>>> OUTPUT: ${line.reverse()}" }
      println ">>>>> EXIT: ${output.exitStatus}"
    }
  }

  @Test
  void testFailedStatus() {
    engine.remoteSession {
      assert exec('i should fail!').failed()
    }
  }

  @Test
  void testFailOnError() throws Exception {
    engine.remoteSession {
      exec(command: 'abcd', failOnError: false)
    }
  }

  @Test
  void testTimeout() throws Exception {
    try {
      engine.remoteSession {
        exec(command: 'timeout', maxWait: 1000)
      }
    } catch (SshException e) {
      assert e.cause.message.contains('timeout')
    }
  }

  @Test
  void testExecClosure() throws Exception {
    // Test closure based builder for exec.
    engine.remoteSession { exec { command = 'whoami' } }
  }

  @Test
  void testCopy() throws Exception {
    engine.remoteSession {
      scp {
        from {
          localDir new File(getCurrentDir(), 'test-settings')
        }
        into { remoteDir '/tmp/puppet' }
      }
    }
  }

  @Test
  void testSudoCopy() throws Exception {
    engine.remoteSession {
      scp {
        uploadToDirectory = '/tmp'
        from {
          localDir new File(getCurrentDir(), 'test-settings')
        }
        into { remoteDir '/etc/puppet' }
      }
    }
  }

  @Test
  void testMultiExec() throws Exception {
    engine.remoteSession {
      exec([
          'ls -la',
          'whoami'
      ])
      exec(failOnError: false, showOutput: true, command: [
          'ls -la',
          'whoami'
      ])
    }
  }


  @Test
  void testEscaping() throws Exception {
    engine.remoteSession {
      exec(command: 'ls -la "\\', escapeCharacters: '"\\')
      exec(command: 'ls -la "\\', escapeCharacters: ['"', '\\'])
      exec(command: 'ls -la "\\', escapeCharacters: ['"', '\\'] as char[])
    }
  }

  @Test
  void testPrefix() throws Exception {
    engine.remoteSession {
      prefix('sudo') {
        exec([
            'ls -la',
            'whoami'
        ])
      }
    }
  }

  @Test
  void testRemoteFile() throws Exception {
    engine.remoteSession {
      remoteFile('/etc/init.conf').text = 'content'
    }
  }

  @Test
  void testRemoteFileIsFile() throws Exception {
    engine.remoteSession {
      assert remoteFile('/etc/init.conf').file
      assert !remoteFile('/etc/init.conf').directory
      assert remoteFile('/etc').directory
      assert !remoteFile('/etc').file
    }
  }

  @Test
  void testOk() throws Exception {
    engine.remoteSession {
      assert ok('whoami')
    }
  }

  @Test
  void testOkWithPrefix() throws Exception {
    engine.remoteSession {
      prefix 'sudo', {
        assert ok('which service')
      }
    }
  }

  @Test
  void testFail() throws Exception {
    engine.remoteSession {
      assert fail('mkdur dur')
    }
  }

  @Test
  void testExecGStringCommand() throws Exception {
    def cmd = 'whoami'
    engine.remoteSession {
      assert exec(command: "$cmd", showOutput: true).output.trim() == 'root'
    }
  }

  @Test
  void testExecGStringCommandArray() throws Exception {
    def cmd = 'whoami'
    List cmds = ["$cmd", "$cmd"]
    engine.remoteSession {
      exec(command: cmds, showOutput: true)
    }
  }

  @Test
  void testExceptionInClosure() throws Exception {
    engine.remoteSession {
      connect()
      disconnect()
    }
    printThreadNames("THREADS BEFORE:")
    List<String> threadsBefore = Thread.allStackTraces.collect { key, value -> key.name }
    try {
      engine.remoteSession {
        exec "whoami"
        throw new TimeoutException("Bang!")
      }
    } catch (e) {
      assert e instanceof TimeoutException
      printThreadNames("THREADS AFTER:")
      List<String> threadsAfter = Thread.allStackTraces.collect { key, value -> key.name }
      assert !threadsAfter.contains('Connect thread 127.0.0.1 session')
    }
  }

  @Test
  void testExecMapValidation() throws Exception {
    engine.remoteSession {
      try {
        exec commandS: "whoami"
        fail("Should not accept missing command parameter")
      } catch(SshException e) {
        assert e.message == "The 'command' parameter is not specified!"
      }
      exec command: "whoami"
    }
  }

  @Test
  void testOptionsOverride() throws Exception {
    String output = captureOutput {
      engine.remoteSession {
        scp {
          showProgress = false
          from {
            localDir new File(getCurrentDir(), 'test-settings')
          }
          into { remoteDir '/tmp/puppet' }
        }
      }
    }
    assert !output.contains('bytes transferred')
  }

}