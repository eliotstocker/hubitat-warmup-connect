/**
 *  Warmup Thermostat
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
 *  Froked from alyc100 Warmup 4ie (https://github.com/alyc100/SmartThingsPublic/blob/master/devicetypes/alyc100/warmup-4ie.src/warmup-4ie.groovy)
 */

import groovy.time.TimeCategory


metadata {
    definition(name: "Warmup Thermostat", namespace: "tv.piratemedia.warmup", author: "Eliot Stocker", ocfDeviceType: "oic.d.thermostat", vid: "36a9d325-53b2-37e8-9376-ee404ac3259d") {
        capability "Actuator"
        capability "Polling"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "Thermostat Heating Setpoint"
        capability "Thermostat Mode"
        capability "Thermostat Operating State"
        capability "Health Check"

        command "temporaryOverride", [
            [
                name: "Boost Time (Minutes)",
                type: "NUMBER"
            ],
            [
                name: "Boost Temperature (˚C)",
                type: "NUMBER"
            ]
        ]
        command "cancelTemporaryOverride"
        
        attribute "boostTimeRemaining", "string"
    }
    
    preferences {
        input("boostMins", "number", title: "Boost Minutes", required: false, defaultValue: "30")
        input("boostTemp", "decimal", title: "Boost Temperature (˚C)", required: false, defaultValue: "24.5")
        input("disableDevice", "bool", title: "Disable Device and force off", required: false, defaultValue: false)
    }
}


#include tv.piratemedia.warmup.WarmupDefinitions


def installed() {
    logDebug "Executing 'installed'"
    state.desiredHeatSetpoint = 7

    runEvery10Minutes(poll)
    sendEvent(name: "checkInterval", value: 20 * 60 + 2 * 60, data: [protocol: "cloud"], displayed: false)

    sendEvent(name: "supportedThermostatFanModes", value: [], displayed: false)
    sendEvent(name: "supportedThermostatModes", value: ["auto", "emergency heat", "heat", "off"], displayed: false)
    
    sendEvent(name: "coolingSetpoint", value: 0)
    sendEvent(name: "thermostatFanMode", value: "off")
}


def updated() {
    logDebug "Executing 'updated'"

    unschedule()
    runEvery10Minutes(poll)
    sendEvent(name: "checkInterval", value: 20 * 60 + 2 * 60, data: [protocol: "cloud"], displayed: false)

    sendEvent(name: "supportedThermostatFanModes", value: [], displayed: false)
    sendEvent(name: "supportedThermostatModes", value: ["auto", "emergency heat", "heat", "off"], displayed: false)
    
    sendEvent(name: "coolingSetpoint", value: 0)
    sendEvent(name: "thermostatFanMode", value: "off")
}


def uninstalled() {
    logDebug "Executing 'uninstalled'"
    
    unschedule()
}

def off() {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_OFF, [device: device.deviceNetworkId])
    poll()
}


def on() {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_PROGRAM, [device: device.deviceNetworkId])
    poll()
}


def heat() {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_FIXED, [device: device.deviceNetworkId])
    poll()
}


def auto() {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_PROGRAM, [device: device.deviceNetworkId])
    poll()
}


def emergencyHeat() {
    def mins = settings.boostMins != null ? settings.boostMins : 30
    def temp = settings.boostTemp != null ? settings.boostTemp : 24.5
    
    logDebug "temp override: ${mins} - ${temp}"
    temporaryOverride(mins, temp)
}


def temporaryOverride(time, temperature) {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_OVERRIDE, [device: device.deviceNetworkId, temp: (temperature * 10) as Integer, mins: time as Integer])
    poll()
}

def cancelTemporaryOverride() {
    parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_CANCEL_OVERRIDE, [device: device.deviceNetworkId])
    poll()
}


def setHeatingSetpoint(temp) {
    logDebug "Executing 'setHeatingSetpoint with temp $temp'"

    if (settings.disableDevice == null || settings.disableDevice == false) {
        parent.graphPOSTByChild(GRAPH_MUTATION_DEVICE_FIXED_TEMP, [device: device.deviceNetworkId, temp: (temp * 10) as Integer])
    }

    runIn(3, refresh)
}


def setTemperature(value) {
    logDebug "Executing 'setTemperature with $value'"
    setHeatingSetpoint(value)
}

def setThermostatMode(mode) {
    if (settings.disableDevice == null || settings.disableDevice == false) {
        mode = mode == 'cool' ? 'heat' : mode
        
        logDebug "Executing 'setThermostatMode with mode $mode'"
        
        if (mode == 'off') {
            off()
        } else if (mode == 'heat') {
            heat()
        } else if (mode == 'emergency heat') {
            emergencyHeat()
        } else {
            auto()
        }

        mode = mode == 'range' ? 'auto' : mode
    }
}


def poll() {
    logDebug "Executing 'poll'"
    def room = parent.getDeviceStatus(device.deviceNetworkId)
    if (room == []) {
        log.error("Unexpected result in parent.getDeviceStatus()")
        return []
    }

    parseDeviceStatus(room)
}

def parseDeviceStatus(status) {
    logDebug "status: ${device.deviceNetworkId} ${status}"
    
    def mode = status.runMode
    if (mode == "fixed") mode = "heat"
    
    else if (mode == "anti_frost") mode = "off"
    else if (mode == "prog" || mode == "schedule") mode = "auto"
    else if (mode == "override") mode = "emergency heat"
        
    sendEvent(name: 'thermostatMode', value: mode)

    //If Warmup heating device is set to disabled, then force off if not already off.
    if (settings.disableDevice != null && settings.disableDevice == true && mode != "off") {
        return off()
    }
    
    if (mode == "emergency heat") {
        def boostTime = status.overrideDur
        boostLabel = boostTime + "min(s)"
        sendEvent("name": "boostTimeRemaining", "value": boostTime + " mins")
    } else {
        device.deleteCurrentState('boostTimeRemaining')
    }

    def temperature = String.format("%2.1f", (status.currentTemp as BigDecimal) / 10)
    sendEvent(name: 'temperature', value: temperature, unit: "C", state: "heat")

    def heatingSetpoint = String.format("%2.1f", (status.targetTemp as BigDecimal) / 10)
    sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, unit: "C", state: "heat")
    sendEvent(name: 'thermostatSetpoint', value: heatingSetpoint, unit: "C", state: "heat", displayed: false)

    if ((status.targetTemp as BigDecimal) > (status.currentTemp as BigDecimal)) {
        sendEvent(name: 'thermostatOperatingState', value: "heating")
    } else {
        sendEvent(name: 'thermostatOperatingState', value: "idle")
    }

    sendEvent(name: "supportedThermostatFanModes", value: [], displayed: false)
    sendEvent(name: "supportedThermostatModes", value: ["emergency heat", "heat", "off"], displayed: false)
}


def refresh() {
    logDebug "Executing 'refresh'"
    poll()
}


def logDebug(msg) {
    if (parent.debug) {
        log.debug msg
    }
}