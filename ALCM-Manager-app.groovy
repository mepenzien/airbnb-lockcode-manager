import java.time.format.DateTimeFormatter

def setVersion(){
  state.name = "AirBNB Lock Code Manager"
	state.version = "0.2"
}

definition(
    name: "ALCM-Manager-app",
    namespace: "mepholdings",
    author: "Mark E Penzien",
    description: "Manages per-listing settings, timing and lock-to-unit associations.",
    category: "Convenience",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: "")

preferences {
/*  app name: "abnb-listing-child-app",
    appName: "AirBNB-LCM-Listing-app",
    namespace: "mepholdings",
    title: "Create a new AirBNB Listing app...",
    multiple: true */
  page name: "mainPage", title: "First Page", install: true, uninstall: true
  page name: "urlOptions", title: "", install: false, uninstall: true, nextPage: "mainPage"
  page name: "unitPage", title: "Child Devices for Units", install: false, uninstall: false, nextPage: "mainPage"
  page name: "listingPage", title: "Child Apps for Listings", install: false, uninstall: false, nextPage: "mainPage"
}

mappings {
  path("/addrsvn") { action: [GET: "addRsvn"]}
  path("/deletersvn") { action: [GET: "deleteRsvn"]}
}

def mainPage() {
  dynamicPage(name: "", title: "", install: true, uninstall: true) {
    installCheck()
    if(state.appInstalled == 'COMPLETE'){
      section(getFormat("header-green", "General")) {
        label title: "Change the name for this parent app (optional)", required: false
        input "logEnable", "bool", defaultValue: "false", title: "Enable Debug Logging", description: "Enable extra logging for debugging."  
      }
      section(getFormat("line")) {}
      section(getFormat("header-green", "URL Options")) {
        href "urlOptions", title:"Maker API Setup", description: "Click here for Options"
      }
      section(getFormat("header-green", "Child Apps")) {
        paragraph "The ALCM Master app needs child devices for Units and child apps for Listings."
        paragraph "Using the following two sections, create the various virtual devices necessary so that there is on Unit device for each physically existing apartment.  Then create a separate child app for each listing that exists on AirBNB that will be managed by this parent app."
        href "unitPage", title: "Units", description: "Click here for creating child devices for each Unit"
        href "listingPage", title: "AirBNB Listings", description: "Click here for creating child apps for each Listing"
      }
      display2()
    } else {
      section(getFormat("header-green", "General")) {
        label title: "Enter a name for this parent app (optional)", required: false, defaultValue: "ALCM-Master-App"
      }
    }
  }
}

// ********** Start - Child Pages **********

def unitPage() {
  dynamicPage(name: "unitPage", title: "") {
    display()
    section(getFormat("header-green", "Instructions")) {
      paragraph "To create new devices for Units, switch the slider on.  Each time you type a name in the field and then hit Enter, a new device will be created automatically.  Once all necessary devices have been created, be sure to select them in the drop down field."
    }
    section(getFormat("header-green", "Children Devices: Individual Units")) {
      paragraph "There should be a separate child device for each individual unit."
      input "useExistingDevice", "bool", title: "Use existing device (off) or create a new one automatically (on)", defaultValue:false, submitOnChange:true
      if(useExistingDevice) {
        input "childDeviceName", "text", title: "Enter a name for this child device; it is advisable to use a name with the following format: \"ALCM Unit - <Unit Name>\".", required:true, submitOnChange:true
        paragraph "<b>A device will automatically be created for you as soon as you click outside of this field.</b>"
        if(childDeviceName) createUnitChildDevice()
        if(statusMessageD == null) statusMessageD = "Waiting on status message..."
        paragraph "${statusMessageD}"
      }
      input "alcmUnitDevice", "device.ALCM-Unit-driver", title: "ALCM Unit devices", required:true, multiple:true
      if(!useExistingDevice) {
        app.removeSetting("childDeviceName")
        paragraph "<small>* Device must use the 'ALCM-Unit-driver' driver.</small>"
      }
    }
  }
}
def listingPage() {
  dynamicPage(name: "unitPage", title: "", install:false, uninstall:false, nextPage: "mainPage") {
    display()
    section(getFormat("header-green", "Instructions")) {
      paragraph "Now that the Unit devices are created, create the child apps for each AirBNB Listing."
    }
    section(getFormat("header-green", "Children Apps: AirBNB Listings")) {
      paragraph "There needs to be a child app for each listing that will receive reservations.  <small>After creating a new Child App for each Listing, the selection of Unit devices is cleared; be sure to reselect them after all child apps exist."
      app(name: "anyOpenApp", appName: "ALCM-Listing-app", namespace: "mepholdings", title: "<b>Add a new AirBNB Listing child-app</b>", multiple: true)
    }
  }
}

def createUnitChildDevice() {    
    if(logEnable) log.debug "In createDataChildDevice (${state.version})"
    statusMessageD = ""
    if(!getChildDevice(childDeviceName)) {
        if(logEnable) log.debug "In createDataChildDevice - Child device not found - Creating device: ${childDeviceName}"
        try {
            addChildDevice("mepholdings", "ALCM-Unit-driver", childDeviceName, ["name": "${childDeviceName}", isComponent: false])
            if(logEnable) log.debug "In createDataChildDevice - Child tile device has been created! (${childDeviceName})"
            statusMessageD = "<b>Device has been been created. (${childDeviceName})</b>"
        } catch (e) { if(logEnable) log.debug "Device Watchdog unable to create data device - ${e}" }
    } else {
        statusMessageD = "<b>Device Name (${childDeviceName}) already exists.</b>"
    }
    return statusMessageD
}

// ********** End - Child Pages **********

// ********** Start - URL Options **********

def urlOptions() {
    dynamicPage(name: "urlOptions", title: "", install:false, uninstall:false) {
        display()
    
        section(getFormat("header-green", "Maker API Config")) {
          paragraph "The information needed below can be retrieved from the main configuration page for the Maker API app."
            input "hubIP", "text", title: "Hub IP Address<br><small>(ie. 192.168.86.81)</small>", submitOnChange:true
            input "cloudToken", "password", title: "Hub Cloud Token (optional)<br><small>(ie. found after the /api/ kdj3-dj3-dkfjj3-kdjfak4-akdjdke55)</small>", submitOnChange:true

            input "makerID", "text", title: "Maker API App Number<br><small>(ie. 104)</small>", width:6, submitOnChange:true
            input "accessToken", "password", title: "Maker API Access Token<br><small>(ie. kajdkfj-3kd8-dkjf-akdjkdf)</small>", width:6, submitOnChange:true
        }
      section(getFormat("header-green", "Access Token")) {
        paragraph "<b>KEEP THIS SECRET AND SECURE!!!</b><br>Access tokens are like the keys to your car: if they get stolen, it's a bad thing!"
        if(state.accessToken) {
          paragraph "The access token for this app is <b>${state.accessToken}</b>"
          paragraph "The endpoint for sending a reservation is: <br><b>${state.rsvnUrl}</b>"
        }
      }
    }
}


// ********** End - URL Options **********

def installCheck(){
  display()
  state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else{
    	log.info "Parent Installed OK"
  	}
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
  log.info "There are ${childApps.size()} child apps"
  childApps.each {child ->
  	log.info "Child app: ${child.label}"
  }
  if(!state.accessToken) {
    createAccessToken()
  }
  state.rsvnUrl = "${fullApiServerUrl("rsvn")}?access_token=${state.accessToken}"
}

def getFormat(type, myText="") {			// Modified from @Stephack Code   
  if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
  if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
  if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def display() {
  setVersion()
  theName = app.label
  if(theName == null || theName == "") theName = "New Child App"
  section (getFormat("title", "${state.name} - ${theName}")) {
    paragraph "Parent App for controlling lock code management for multiple AirBNB listings/units, with multiple code-enabled locks."
    paragraph getFormat("line")
  }
}

def display2() {
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center;font-size:20px;font-weight:bold'>${state.name} - ${state.version}</div>"
	}       
}

def addRsvn() {
  log.warn "addRsvn has been called with params: ${params}"
  slurper = new groovy.json.JsonSlurper()
  if(params.access_token) params.remove("access_token")
  incomingRsvn = [:]
  params.each {
    incomingRsvn.put(it.key, it.value)
    //log.info "${incomingRsvn.key}"
  }
  
  incomingRsvn.code = incomingRsvn.code.toLong()
  incomingRsvn.start = incomingRsvn.start.toLong()
  incomingRsvn.end = incomingRsvn.end.toLong()
  
  // change sendTime string to DateTime object
  format = "yyyy-MM-dd'T'HH:mm:ss.SSSXX"
  DateTimeFormatter f = DateTimeFormatter.ofPattern(format)
  CharSequence csD = params.sendTime
  timestamp = LocalDate.parse(csD,f)
  incomingRsvn.remove("sendTime")
  incomingRsvn.put(sendTime, timestamp)
  
  getChildApps().each {
    it.processIncomingRsvn(incomingRsvn)
  }
  return "Success!"
}

def deleteRsvn() {
  log.warn "deleteRsvn has been called with params: ${params}"
  slurper = new groovy.json.JsonSlurper()
  if(params.access_token) params.remove("access_token")
  cancelledRsvn = [:]
  params.each {
    cancelledRsvn.put(it.key, it.value)
    //log.info "${cancelledRsvn.key}"
  }
  
  cancelledRsvn.code = cancelledRsvn.code.toLong()
  cancelledRsvn.start = cancelledRsvn.start.toLong()
  cancelledRsvn.end = cancelledRsvn.end.toLong()
  
  // change sendTime string to DateTime object
  format = "yyyy-MM-dd'T'HH:mm:ss.SSSXX"
  DateTimeFormatter f = DateTimeFormatter.ofPattern(format)
  CharSequence csD = params.sendTime
  timestamp = LocalDate.parse(csD,f)
  cancelledRsvn.remove("sendTime")
  cancelledRsvn.put(sendTime, timestamp)
  
  getChildApps().each {
    it.processCancelledRsvn(cancelledRsvn)
  }
  return "Success!"
}


  
  
