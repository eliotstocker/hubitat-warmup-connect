/**
 *  Warmup Connect
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
 *  Froked from alyc100 Warmup (Connect) (https://github.com/alyc100/SmartThingsPublic/blob/master/smartapps/alyc100/warmup-connect.src/warmup-connect.groovy)
 */


definition(
    name: "Warmup Connect",
    namespace: "tv.piratemedia.warmup",
    author: "Eliot Stocker",
    description: "Connect your Warmup devices to Hubitat.",
    category: "",
    iconUrl: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/warmup-icon.png",
    iconX2Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/warmup-icon.png",
    iconX3Url: "https://raw.githubusercontent.com/alyc100/SmartThingsPublic/master/smartapps/alyc100/warmup-icon.png",
    singleInstance: true
)

preferences {
    page(name: "setupPage", title: "Warmup Device Setup", content: "setupPage", install: true)
    page(name: "loginPage")
    page(name: "selectDevicePage")
    page(name: "resetDevices")
}


#include tv.piratemedia.warmup.WarmupDefinitions


private getRestUrl() { URL_REST_ENDPOINT }
private getGraphUrl() { URL_GRAPH_ENDPOINT }


def setupPage() {
    if (username == null || username == '' || password == null || password == '') {
        return dynamicPage(name: "setupPage", title: "Setup Warmup Connect", install: true, uninstall: true) {
            section {
                headerSection()
                href("loginPage", title: "Authenticate Account", description: authenticated() ? "Authenticated as " + username : "Tap to enter Warmup account credentials", state: authenticated())
            }
        }
    } else {
        return dynamicPage(name: "setupPage", title: "Warmup Connect Settings", install: true, uninstall: true) {
            section {
                headerSection()
                href("loginPage", title: "Authentication", description: authenticated() ? "Authenticated as " + username : "Tap to enter Warmup account credentials", state: authenticated())
            }

            if (stateTokenPresent()) {
                section("Choose your Warmup devices:") {
                    href("selectDevicePage", title: "Thermostats", description: devicesSelected() ? getDevicesSelectedString() : "Tap to select Warmup devices", state: devicesSelected())
                }
            } else {
                section {
                    paragraph "There was a problem connecting to Warmup. Check your user credentials and error logs in SmartThings web console.\n\n${state.loginerrors}"
                }
            }

            section("App Settings") {
                input("debug", "bool", title: "Enable debug logging", description: "enable verbose logging from the application")
            }
        }
    }
}


def loginPage() {
    if (username == null || username == '' || password == null || password == '') {
        return dynamicPage(name: "loginPage", title: "Warmup Connect Authentication Setup", uninstall: false, install: false) {
            section {
                headerSection()
            }

            section {
                paragraph "Enter your Warmup account credentials below to enable SmartThings and Warmup integration."
            }

            section {
                input("username", "text", title: "Username", description: "Your Warmup username (usually an email address)", required: true)
                input("password", "password", title: "Password", description: "Your Warmup password", required: true, submitOnChange: true)
            }
        }
    } else {
        getWarmupAccessToken()
        dynamicPage(name: "loginPage", title: "Warmup Connect Authentication Settings", uninstall: false, install: false) {
            section {
                headerSection()
            }

            section {
                paragraph "Enter your Warmup account credentials below to enable SmartThings and Warmup integration."
            }

            section("Warmup Credentials:") {
                input("username", "text", title: "Username", description: "Your Warmup username (usually an email address)", required: true)
                input("password", "password", title: "Password", description: "Your Warmup password", required: true, submitOnChange: true)
            }

            if (stateTokenPresent()) {
                section {
                    paragraph "You have successfully connected to Warmup. Click 'Done' to select your Warmup devices."
                }
            } else {
                section {
                    paragraph "There was a problem connecting to Warmup. Check your user credentials and error logs in SmartThings web console.\n\n${state.loginerrors}"
                }
            }
        }
    }
}


def selectDevicePage() {
    updateLocations()
    dynamicPage(name: "selectDevicePage", title: "Device Selection", uninstall: false, install: false) {
        section {
            headerSection()
        }

        if (devicesSelected() == null) {
            section("Select your Location:") {
                input "selectedLocation", "enum", required: false, title: "Select a Location \n(${state.warmupLocations.size() ?: 0} found)", multiple: false, options: state.warmupLocations, submitOnChange: true
            }
        } else {
            section("Your location:") {
                paragraph("Location: ${state.warmupLocations[selectedLocation]}")
                href("resetDevices", title: "Clear Location and Remove Devices", description: "You must clear all device in order to change location")
            }
        }

        if (selectedLocation) {
            updateDevices()

            section("Select your devices:") {
                input "selectedDevices", "enum", required: false, title: "Select Warmup Devices \n(${state.warmupDevices.size() ?: 0} found)", multiple: true, options: state.warmupDevices
            }
        }
    }
}

def resetDevices() {
    app.removeSetting("selectedLocation")
    app.removeSetting("selectedDevices")
    
    updateDevices();
    
    dynamicPage(name: "resetDevices", title: "Location Cleared", uninstall: false, install: false) {
        section {
            headerSection()
            paragraph("Devices removed and location reset, click done bellow to go back to lection selection...")
        }
    }
}


def headerSection() {
    return paragraph("${textVersion()}")
}


def stateTokenPresent() {
    return state.warmupAccessToken != null && state.warmupAccessToken != ''
}


def authenticated() {
    return (state.warmupAccessToken != null && state.warmupAccessToken != '') ? "complete" : null
}


def devicesSelected() {
    return (selectedDevices) ? "complete" : null
}


def getDevicesSelectedString() {
    if (state.warmupDevices == null) {
        updateDevices()
    }

    def listString = ""
    selectedDevices.each {
        childDevice ->
            if (state.warmupDevices[childDevice] != null) listString += state.warmupDevices[childDevice] + "\n"
    }

    return listString
}

// App lifecycle hooks

def installed() {
    logDebug "installed"
    initialize()
    // Check for new devices and remove old ones every 3 hours
    runEvery3Hours('updateDevices')
    // execute refresh method every minute
    schedule("0 0/1 * * * ?", refreshDevices)
}

// called after settings are changed
def updated() {
    logDebug "updated"
    initialize()
    unschedule('refreshDevices')
    schedule("0 0/10 * * * ?", refreshDevices)
}


def uninstalled() {
    log.info("Uninstalling, removing child devices...")
    unschedule()
    removeChildDevices(getChildDevices())
}


private removeChildDevices(devices) {
    devices.each {
        deleteChildDevice(it.deviceNetworkId) // 'it' is default
    }
}

// called after Done is hit after selecting a Location
def initialize() {
    logDebug "initialize"
    if (selectedDevices) {
        addThermostats()
    }

    def devices = getChildDevices()
    devices.each {
        logDebug "Refreshing device $it.name"
        it.refresh()
    }
}


def updateDevices() {
    if (!state.devices) {
        state.devices = [: ]
    }

    def devices = getLocationDevices()
    state.warmupDevices = [: ]

    def selectors = []
    devices.each {
        device ->
            logDebug "Identified: device ${device.id}: ${device.type}: ${device.roomName}: ${device.targetTemp}: ${device.currentTemp}"
        selectors.add("${device.id}")
        def value = "${device.roomName} Thermostat"
        def key = device.id
        state.warmupDevices["${key}"] = value

        def childDevice = getChildDevice("${device.id}")
        if (childDevice) {
            //Update name of device if different.
            if (childDevice.name != device.roomName + " Thermostat") {
                childDevice.name = device.roomName + " Thermostat"
                logDebug "Device's name has changed."
            }
        }
    }

    logDebug selectors
    //Remove devices if does not exist on the Warmup platform
    getChildDevices().findAll {
        !selectors.contains("${it.deviceNetworkId}")
    }.each {
        log.info("Deleting ${it.deviceNetworkId}")
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (hubitat.exception.NotFoundException e) {
            log.info("Could not find ${it.deviceNetworkId}. Assuming manually deleted.")
        } catch (hubitat.exception.ConflictException ce) {
            log.info("Device ${it.deviceNetworkId} in use. Please manually delete.")
        }
    }
}


def updateLocations() {
    def locations = getLocations()
    state.warmupLocations = [: ]

    def selectors = []
    locations.each {
        location ->
            logDebug "Identified: location ${location.id}: ${location.name}"
        selectors.add("${location.id}")
        def value = "${location.name}"
        def key = location.id
        state.warmupLocations["${key}"] = value
    }

    logDebug selectors
}


def addThermostats() {
    updateDevices()

    selectedDevices.each {
        device ->

            def childDevice = getChildDevice("${device}")
        if (!childDevice && state.warmupDevices[device] != null) {
            log.info("Adding device ${device}: ${state.warmupDevices[device]}")

            def data = [
                name: state.warmupDevices[device],
                label: state.warmupDevices[device]
            ]
            childDevice = addChildDevice("tv.piratemedia", "Warmup Thermostat", "$device", null, data)

            logDebug "Created ${state.warmupDevices[device]} with id: ${device}"
        } else {
            logDebug "found ${state.warmupDevices[device]} with id ${device} already exists"
        }

    }
}


def refreshDevices() {
    log.info("Executing refreshDevices...")
    getChildDevices().each {
        device ->
            log.info("Refreshing device ${device.name} ...")
        device.refresh()
    }
}


def getLocations() {
    logErrors([]) {
        def resp = graphPOST(GRAPH_QUERY_LIST_LOCATIONS)
                
        if (resp.status == 200 && resp.data.status == "success") {
            return resp.data.data.user.locations
        } else {
            log.error("Non-200 from location list call. ${resp.status} ${resp.data}")
            return []
        }
    }
}

def getLocationDevices() {    
    logErrors([]) {
        def resp = graphPOST(GRAPH_QUERY_LIST_ROOMS, [
            location: selectedLocation
        ])
                
        if (resp.status == 200 && resp.data.status == "success") {
            return resp.data.data.user.location.rooms
        } else {
            log.error("Non-200 from device status call. ${resp.status} ${resp.data}")
            return []
        }
    }
}

def getDeviceStatus(rid) {
    logErrors([]) {
        def resp = graphPOST(GRAPH_QUERY_ROOM_STATUS, [
            location: selectedLocation,
            room: rid
        ])
                
        if (resp.status == 200 && resp.data.status == "success") {
            return resp.data.data.user.location.room
        } else {
            log.error("Non-200 from device status call. ${resp.status} ${resp.data}")
            return []
        }
    }
}


def getWarmupAccessToken() {
    def body = [
        request: [
            "email": "${username}",
            "password": "${password}",
            "method": "userLogin",
            "appId": "WARMUP-APP-V001"
        ]
    ]
    def resp = apiPOST(body)
    logDebug resp
    if (resp.status == 200) {
        if (resp.data.status.result == "success" && resp.data.status.result == "success") {
            state.warmupAccessToken = resp.data.response.token
            logDebug "warmupAccessToken: $resp.data.response.token"
        } else {
            log.error("Non-200 from device list call. ${resp.status} ${resp.data}")
            return []
        }
    }
}

def apiPOST(body = [: ]) {
    def bodyString = new groovy.json.JsonBuilder(body).toString()
    return makePostRequest(bodyString, getRestUrl(), restRequestHeaders());
}


private def makePostRequest(body = "", endpoint, headers) {
    logDebug("Beginning API POST: ${endpoint}, ${body}, ${headers}")
    try {
        def result
        httpPost(uri: endpoint, body: body, headers: headers) {
            response ->
                logResponse(response)
            result = response
        }

        return result
    } catch (groovyx.net.http.HttpResponseException e) {
        logResponse(e.response, true)
        return e.response
    }
}


def graphPOSTByChild(body = "", variables = [:]) {
    return graphPOST(body, variables + [location: selectedLocation])
}


def graphPOST(query = "", variables = [:]) {
    return makePostRequest("{\"query\": \"${query}\", \"variables\": ${new groovy.json.JsonBuilder(variables).toString()}}", getGraphUrl(), graphRequestHeaders())
}


def apiPOSTByChild(args = [: ]) {
    def body = [
        account: [
            "email": "${username}",
            "token": "${state.warmupAccessToken}"
        ],
        request: args
    ]
    return apiPOST(body)
}


def setLocationToFrost() {
    def body = [
        account: [
            "email": "${username}",
            "token": "${state.warmupAccessToken}"
        ],
        request: [
            method: "setModes", values: [holEnd: "-", fixedTemp: "", holStart: "-", geoMode: "0", holTemp: "-", locId: "${selectedLocation}", locMode: "frost"]
        ]
    ]
    return apiPOST(body)
}


def setRoomToFrostProtectionMode(String locationId, String roomId) {
    graphPOST(String.format("mutation{turnOff(lid:%s,rid:%s){id}}", locationId, roomId));
}


private Map baseRequestHeaders() {
    return [
        "Content-Type": "application/json",
        "App-Token": "M=;He<Xtg\"\$}4N%5k{\$:PD+WA\"]D<;#PriteY|VTuA>_iyhs+vA\"4lic{6-LqNM:",
        "User-Agent": "WARMUP_APP",
        "App-Version": "1.8.1",
    ]
}


private Map restRequestHeaders() {
    return baseRequestHeaders()
}


private Map graphRequestHeaders() {
    return baseRequestHeaders() + ["Warmup-Authorization": state.warmupAccessToken]
}


def logDebug(msg) {
    if (debug) {
        log.debug msg
    }
}


def logResponse(response) {
    logResponse(response, false)
}


def logResponse(response, always) {
    if (debug || always) {
        log.info("Status: ${response.status}")
        log.info("Body: ${response.data}")
    }
}


def logErrors(options = [errorReturn: null, logObject: log], Closure c) {
    try {
        return c()
    } catch (groovyx.net.http.HttpResponseException e) {
        options.logObject.error("got error: ${e}, body: ${e.getResponse().getData()}")
        if (e.statusCode == 401) { // token is expired
            state.remove("warmupAccessToken")
            options.logObject.warn "Access token is not valid"
        }

        return options.errorReturn
    } catch (java.net.SocketTimeoutException e) {
        options.logObject.warn "Connection timed out, not much we can do here"
        return options.errorReturn
    }
}


private def textVersion() {
    def text = "Version: 1.0.1 BETA\nDate: 15.01.2023"
}