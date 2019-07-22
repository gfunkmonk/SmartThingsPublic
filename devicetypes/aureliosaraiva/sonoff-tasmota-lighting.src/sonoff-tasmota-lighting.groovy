metadata {
	definition(name: "Sonoff-Tasmota-Lighting", namespace: "AurelioSaraiva", author: "Aurélio Saraiva", ocfDeviceType: "oic.d.light") {
		capability "Actuator"
		capability "Switch"
		capability "Refresh"
        capability "Light"
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 1, height: 1, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
            	attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
            	attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
		}

		main "switch"
		details(["switch"])
	}

	preferences {
		input(name: "ipAddress", type: "string", title: "IP Address", description: "IP Address of Sonoff", displayDuringSetup: true, required: true)
		input(name: "port", type: "number", title: "Port", description: "Port", displayDuringSetup: true, required: true, defaultValue: 80)

		section("Sonoff Host") {

		}

		section("Authentication") {
			input(name: "username", type: "string", title: "Username", description: "Username", displayDuringSetup: false, required: false)
			input(name: "password", type: "password", title: "Password (sent cleartext)", description: "Caution: password is sent cleartext", displayDuringSetup: false, required: false)
		}
	}

	simulator {
    }
}

def updated() {
	refresh()
}

def parse(String description) {
	def message = parseLanMessage(description)

	// parse result from current and legacy formats
	def resultJson = {}
	if (message?.json) {
		// current json data format
		resultJson = message.json
	}
	else {
		// legacy Content-Type: text/plain
		// with json embedded in body text
		def STATUS_PREFIX = "STATUS = "
		def RESULT_PREFIX = "RESULT = "
		if (message?.body?.startsWith(STATUS_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(STATUS_PREFIX.length()))
		}
		else if (message?.body?.startsWith(RESULT_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(RESULT_PREFIX.length()))
		}
	}

	// consume and set switch state
	if ((resultJson?.POWER in ["ON", 1, "1"]) || (resultJson?.Status?.Power in [1, "1"])) {
		setSwitchState(true)
	}
	else if ((resultJson?.POWER in ["OFF", 0, "0"]) || (resultJson?.Status?.Power in [0, "0"])) {
		setSwitchState(false)
	}
	else {
		log.error "can not parse result with header: $message.header"
		log.error "...and raw body: $message.body"
	}
}

def setSwitchState(Boolean on) {
	log.info "switch is " + (on ? "ON" : "OFF")
	sendEvent(name: "switch", value: on ? "on" : "off")
}

def on() {
	log.debug "ON"
	sendCommand("Power%20On")
}

def off() {
	log.debug "OFF"
	sendCommand("Power%20Off")
}

def refresh() {
	log.debug "REFRESH"
	sendCommand("Status", null)
}

private def sendCommand(String command) {
	log.debug "sendCommand(${command}) to device at $ipAddress:$port"

	if (!ipAddress || !port) {
		log.warn "aborting. ip address or port of device not set"
		return null;
	}
	def hosthex = convertIPtoHex(ipAddress)
	def porthex = convertPortToHex(port)
	device.deviceNetworkId = "$hosthex:$porthex"

	def path = "/cm?cmnd=${command}"

	if (username){
		path += "&user=${username}"
		if (password){
			path += "&password=${password}"
		}
	}

	log.debug "${path}"
	def result = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [
			HOST: "${ipAddress}:${port}"
		]
	)
	return result
}

private String convertIPtoHex(ipAddress) {
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}