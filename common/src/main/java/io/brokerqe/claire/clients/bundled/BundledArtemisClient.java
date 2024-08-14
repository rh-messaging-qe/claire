/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.bundled;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.clients.DeployableClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BundledArtemisClient {

    private final Map<String, String> commandOptions;
    private ArtemisCommand artemisCommand;
    private DeployableClient deployableClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(BundledArtemisClient.class);
    private String username;
    private String password;
    private String destination;

    public BundledArtemisClient(DeployableClient deployableClient, ArtemisCommand artemisCommand, Map<String, String> commandOptions) {
        this(deployableClient, artemisCommand, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, commandOptions);
    }

    public BundledArtemisClient(DeployableClient deployableClient, ArtemisCommand artemisCommand, Map<String, String> commandOptions, String destination) {
        this(deployableClient, artemisCommand, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, commandOptions);
        this.destination = destination;
    }

    public BundledArtemisClient(DeployableClient deployableClient, ArtemisCommand artemisCommand, String username, String password, Map<String, String> commandOptions) {
        this.deployableClient = deployableClient;
        this.artemisCommand = artemisCommand;
        this.username = username;
        this.password = password;
        this.commandOptions = commandOptions;
    }

    private String[] constructClientCommand() {
        // ./amq-broker/bin/artemis COMMAND --url tcp://10.129.2.15:61616 --destination queue://demoQueue --message-count=50
        // ./amq-broker/bin/artemis COMMAND --user admin --password admin
        // ./amq-broker/bin/artemis address show --verbose --user admin --password admin
        String command = String.format("%s/artemis %s", deployableClient.getExecutableHome(), artemisCommand.getCommand());
        StringBuilder commandBuild = new StringBuilder(command);

        if (username != null) {
            commandBuild.append(" --user=").append(username);
        }
        if (password != null) {
            commandBuild.append(" --password=").append(password);
        }

        for (Map.Entry<String, String> entry : commandOptions.entrySet()) {
            commandBuild.append(" --").append(entry.getKey());
            if (!entry.getValue().equals("")) {
                commandBuild.append("=").append(entry.getValue());
            }
        }

        if (destination != null) {
            commandBuild.append(" ").append(destination);
        }
        return commandBuild.toString().split(" ");
    }

    public Object executeCommand() {
        return executeCommand(Constants.DURATION_3_MINUTES);
    }

    public Object executeCommand(long maxTimeout) {
        String cmdOutput;
        String[] command = constructClientCommand();
        cmdOutput = (String) deployableClient.getExecutor().executeCommand(maxTimeout, command);
        if (artemisCommand.equals(ArtemisCommand.PERF_CLIENT)) {
            LOGGER.debug("[PERF] Client detected, to see it's output use trace logging.");
            LOGGER.trace(cmdOutput);
        } else {
            LOGGER.debug(cmdOutput);
        }
        return parseOutput(cmdOutput);
    }

    private Object parseOutput(String cmdOutput) {
        switch (artemisCommand) {
            case ADDRESS_SHOW -> {
                return List.of(cmdOutput.split("\n"));
            }
            case QUEUE_CREATE -> {
                return cmdOutput;
            }
            case QUEUE_STAT -> {
                return parseQueueCommand(cmdOutput);
            }
            case PERF_CLIENT -> {
                return parsePerfClientOutput(cmdOutput);
            }
            case BROWSE_CLIENT -> {
                return parseBrowseCommand(cmdOutput);
            }

        }
        return null;
    }

    private static Map<String, Map<String, String>> parseQueueCommand(String cmdOutput) {
        // TODO - ideally construct Data Object instead of this Map of information data
        Map<String, String> mappedLineOutput;
        List<String> headers = new ArrayList<String>();
        Map<String, Map<String, String>> mappedQueueData = new HashMap<String, Map<String, String>>();

        for (String line : cmdOutput.split("\n")) {
            if (line.startsWith("|")) {
                if (line.contains("NAME") && line.contains("ADDRESS")) {
                    for (String name : line.split("\\|")) {
                        if (!name.isEmpty()) {
                            headers.add(name.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                // when headers are separated on 2 lines, concat them properly
                } else if (line.contains("COUNT") && line.contains("ACKED") && line.contains("ADDED")) {
                    int counter = -1;
                    for (String name : line.split("\\|")) {
                        if (!name.trim().isEmpty()) {
                            headers.set(counter, headers.get(counter) + "_" + name.trim().toLowerCase(Locale.ROOT));
                        }
                        counter++;
                    }
                } else {
                    mappedLineOutput = new HashMap<>();
                    String[] splittedLine = line.split("\\|");
                    String queueKey = splittedLine[1].trim();
                    int i = 0;
                    for (String split : splittedLine) {
                        if (!split.isEmpty()) {
                            mappedLineOutput.put(headers.get(i), split.trim());
                            i++;
                        }
                    }
                    mappedQueueData.put(queueKey, mappedLineOutput);
                }
            }
        }
        return mappedQueueData;
    }

    private static Map<String, String> parsePerfClientOutput(String cmdOutput) {
        List<String> lines = List.of(cmdOutput.split("\n"));
        Map<String, String> data = new HashMap<>();
        boolean startParsing = false;
        for (String line : lines) {
            if (startParsing) {
                line = line.replaceAll("\\s+", " ").replaceAll("--- ", "");
                if (line.contains("aggregated")) {
                    String key = line.substring(0, line.indexOf(":")).replaceAll(" ", "_");
                    String dataLine = line.substring(line.indexOf(": ") + 1);

                    String[] perc = dataLine.split(" - ");
                    for (String s : perc) {
                        String[] value = s.split(": ");
                        data.put(key + "_us_" + value[0].replace("%", ""), value[1].replaceAll(" us", ""));
                    }
                } else {
                    String[] splitted = line.split(": ");
                    data.put(splitted[0].replaceAll(" ", "_"), splitted[1].trim());
                }
            }
            if (line.contains("SUMMARY")) {
                startParsing = true;
            }
        }
        return data;
    }

    private static Map<Integer, String> parseBrowseCommand(String cmdOutput) {
        List<String> lines = List.of(cmdOutput.split("\n"));
        Map<Integer, String> data = new HashMap<>();
        int msgCounter = 0;
        for (String line : lines) {
            if (line.contains("browsing ")) {
                String msgContent = line.substring(line.indexOf("browsing") + 9);
                data.put(msgCounter, msgContent);
                msgCounter++;
            } else if (line.contains("browsed:")) {
                String totalBrowsedMsgs = line.substring(line.indexOf("browsed:") + 9, line.indexOf(" messages"));
                data.put(-1, totalBrowsedMsgs);
            }
        }
        if (Integer.parseInt(data.get(-1)) != msgCounter) {
            LOGGER.warn("Error while browsing messages! Message count does not equals number of browsed messages!");
        }
        return data;
    }

}
