/**
 *  Warmup Definitions
 *
 *  Copyright 2023 Eliot Stocker
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 
 *    VERSION HISTORY
 *  15.01.2023  v1.0.1 - Rewrite most of the comms to use GraphQL and add ability to turn off individual themostats
 */


library (
    name: "WarmupDefinitions",
    namespace: "tv.piratemedia.warmup",
    author: "Eliot Stocker",
    category: "definitions",
    description: "Defintions for API requests against Warmup APIs (mostly GraphQL)",
    documentationLink: "http://www.github.com/"
)

import groovy.transform.Field


@Field final String URL_REST_ENDPOINT = "https://api.warmup.com/apps/app/v1"


@Field final String URL_GRAPH_ENDPOINT = "https://apil.warmup.com/graphql"


@Field final String GRAPH_QUERY_LIST_LOCATIONS = "query locations {" +
        "user {" +
            "locations {" +
                "id " +
                "name " +
                "zone " +
            "}" +
        "}" +
    "}"


@Field final String GRAPH_QUERY_LIST_ROOMS = "query rooms(\$location: Int!) {" +
        "user {" +
            "location(id: \$location) {" +
                "rooms {" +
                    "id " +
                    "type " +
                    "roomName " +
                "}" +
            "}" +
        "}" +
    "}"


@Field final String GRAPH_QUERY_ROOM_STATUS = "query room(\$location: Int, \$room: Int!) {" +
        "user {" +
            "location(id: \$location) {" +
                "room(id: \$room) {" +
                    "type " +
                    "roomName " +
                    "currentTemp " +
                    "runMode " +
                    "targetTemp " +
                    "overrideTemp " +
                    "overrideDur " +
                    "thermostat4ies {" +
                        "deviceSN " +
                        "lastPoll " +
                    "}" +
                "}" +
            "}" +
        "}" +
    "}"


@Field final String GRAPH_MUTATION_DEVICE_OFF = "mutation turnDeviceOff(\$location: Int!, \$device: Int!) {" +
        "turnOff(lid: \$location, rid: \$device) {" +
            "id" +
        "}" +
    "}"

@Field final String GRAPH_MUTATION_DEVICE_FIXED_TEMP = "mutation setDeviceFixedModeTemp(\$location: Int!, \$device: Int!, \$temp: Int!) {" +
        "deviceFixed(lid: \$location, rid: \$device, temperature: \$temp)" +
    "}"

@Field final String GRAPH_MUTATION_DEVICE_FIXED = "mutation setDeviceFixedMode(\$location: Int!, \$device: Int!) {" +
        "deviceFixed(lid: \$location, rid: \$device)" +
    "}"

@Field final String GRAPH_MUTATION_DEVICE_PROGRAM = "mutation setDeviceProgramMode(\$location: Int!, \$device: Int!) {" +
        "deviceProgram(lid: \$location, rid: \$device)" +
    "}"

@Field final String GRAPH_MUTATION_DEVICE_OVERRIDE = "mutation setDeviceOverride(\$location: Int!, \$device: Int!, \$temp: Int!, \$mins: Int!) {" +
        "deviceOverride(lid: \$location, rid: \$device, temperature: \$temp, minutes: \$mins)" +
    "}"

@Field final String GRAPH_MUTATION_DEVICE_CANCEL_OVERRIDE = "mutation cancelDeviceOverride(\$location: Int!, \$device: Int!) {" +
        "cancelOverride(lid: \$location, rid: \$device) {" +
            "id" +
        "}" +
    "}"