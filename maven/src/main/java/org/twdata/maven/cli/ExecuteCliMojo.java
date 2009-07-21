package org.twdata.maven.cli;

import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManager;
import org.codehaus.plexus.util.StringUtils;
import org.twdata.maven.cli.commands.ExecuteGoalCommand;
import org.twdata.maven.cli.console.JLineCliConsole;

/**
 * Provides an interactive command line interface for Maven plugins, allowing
 * users to execute plugins directly.
 *
 * @requiresDependencyResolution execute
 * @aggregator true
 * @goal execute
 */
public class ExecuteCliMojo extends AbstractCliMojo {
    /**
     * Command aliases. Commands should be in the form GROUP_ID:ARTIFACT_ID:GOAL
     *
     * @parameter
     */
    private Map<String, String> commands;

    /**
     * TCP port to listen to for shell access
     *
     * @parameter expression="${cli.port}"
     */
    private String port = null;

    /**
     * The Maven PluginManager Object
     *
     * @component
     * @required
     */
    protected PluginManager pluginManager;

    private boolean acceptSocket = true;

    private ServerSocket server = null;

    public void execute() throws MojoExecutionException {
        Thread shell = new Thread() {
            @Override
            public void run() {
                try {
                    ExecuteCliMojo.this.displayShell(System.in, System.out);
                    acceptSocket = false;
                    if (server != null) {
                        server.close();
                    }
                }
                catch (MojoExecutionException e) {
                    throw new RuntimeException(e);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        shell.start();

        if (port != null) {
            try {
                server = new ServerSocket(Integer.parseInt(port));
            }
            catch (IOException e) {
                System.out.println("Cannot open port " + port + " for cli server: " + e);
            }
            openSocket(server, Integer.parseInt(port));
        }
        try {
            shell.join();
        }
        catch (InterruptedException e) {
            // ignore
        }

    }

    private void openSocket(ServerSocket server, int port) throws MojoExecutionException {
        System.out.println("Opening port " + port + " for socket cli access");
        while (acceptSocket) {
            Socket connection = null;
            try {
                connection = server.accept();
                displayShell(connection.getInputStream(), new PrintStream(connection.getOutputStream()));
            }
            catch (IOException ex) {
                System.out.println("Server quit unexpectedly");
                ex.printStackTrace();

            }
            finally {
                if (connection != null) {
                    try {
                        connection.close();
                    }
                    catch (IOException e) {
                        // we really don't care
                    }
                }
            }
        }
    }

    private void displayShell(InputStream in, PrintStream out) throws MojoExecutionException {
        JLineCliConsole console = new JLineCliConsole(in, out, getLog(), prompt);
        resolveUserDefinedGoals();
        resolveModulesInProject();

        buildCliCommands();
        console.setCompletor(new CommandsCompletor(buildValidCommandTokens()));

        console.writeInfo("Waiting for commands");
        String line;

        while ((line = console.readLine()) != null) {
            if (StringUtils.isEmpty(line)) {
                continue;
            } else if (interpretCommand(line, console) == false) {
                break;
            }
        }
    }

    private void buildCliCommands() {
        cliCommands.add(new ExecuteGoalCommand(project, session, pluginManager, commands));
        buildDefaultCommands();
    }

    private void resolveUserDefinedGoals() {
        if (commands == null) {
            commands = new HashMap<String, String>();
        }
    }
}
