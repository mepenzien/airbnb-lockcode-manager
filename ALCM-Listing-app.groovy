import java.time.format.DateTimeFormatter
import java.time.LocalTime

definition(
    name: "ALCM-Listing-app",
    namespace: "mepholdings",
    parent: "mepholdings:ALCM-Manager-app",
    author: "Mark E Penzien",
    description: "Manages per-listing settings, timing and lock-to-unit associations.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")


def enableLogging = [
  name:          'logEnabled',
  type:          'bool',
  title:          'Enable Debug Logging?',
  defaultValue:  false,
  required:      true
  ]

preferences {
  page name: 'mainPage', title: 'Preferences', install:true, uninstall: true
  page name: 'timeSettings', title: "Time Settings", nextPage: 'mainPage', uninstall: true
  page name:"testing", title:"testing", uninstall:false, nextPage:"testingOutput"
  page name:"testOutput", title: "testOutput", uninstall:false, nextPage:"mainPage"

}

def mainPage() {
  dynamicPage(name: 'mainPage') {
    section(getFormat("header-green", "Child App Name: provide the AirBNB Listing's name.")){
      //label title: "Customize the name of this child app instance.", required: true, submitOnChange:false
      if(!listingCode) {
        input name: "listingCode", type: "STRING", title: "Enter the shortcode used in Google App Script for this listing.", required: true, submitOnChange:true
        app.updateLabel("ALCM Listing - " + listingCode)
      } else {
        input name: "listingCode", type: "STRING", title: "Enter the shortcode used in Google App Script for this listing.", required: false, defaultValue: listingCode, submitOnChange:true
        app.updateLabel("ALCM Listing - " + listingCode)
      }
    }
/*    section(getFormat("header-green", "TestingPage")) {
      dateStr = "20250314"
      time = "13:00"
      offset = 0
      href "testing", title: "Testing Page", description: "Click here for testing things"
    }*/
    section(getFormat('title', 'Associated Units and Locks')) {
      paragraph "Be sure to have an AirBNB-LCM-Unit device installed for each unit that will be managed.  \
      If the AirBNB listing is for multiple units that can be booked together, each unit device must be selected here. \
      Each lock that will have the guest\'s code installed should also be selected. \
      Units and locks may be associated with multiple listings."
    }
    section('') {
      input name: "units", type: "device.ALCM-Unit-driver", title: "Select which unit(s) are managed with this listing.", required: true, multiple: true
      input name: "locks", type: "capability.lock", title: "Select which lock(s) need to have codes installed/removed for reservations for this listing.", required: true, multiple: true
    }
    section(getFormat('title', 'Time Settings')) {
      href "timeSettings", title: "Time Settings", description: "Click here to choose Check-in/out times, as well as when to install/remove lock codes."
    }
    section() {
      input enableLogging
    }
  }
}

def timeSettings() {
  dynamicPage(name: 'timeSettings') {
    section(getFormat('header-green', 'Select the normal and early/late check-in/out times.')) {
      paragraph "Select the normal check-in and check-out times, as well as the early check-in and late check-out times. \
      Any lock activity using the guest's code before normal check-in or after normal check-out will be logged and alerted. \
      Lock codes will be installed at early check-in time, and removed at late check-out time."
    }
    section('<b>Normal Check-in/out</b>') {
      input name: "checkIn", type: "time", title: "What time is normal Check-In? <small>(default: 15:00)</small>", required: false, default: "15:00"
      input name: "checkOut", type: "time", title: "What time is normal Check-Out? <small>(default: 10:00)</small>", required: false, default: "10:00"
    }
    section('<b>Early/Late Check-in/out AKA lock code installation/removal</b>') {
      paragraph "Any usage of the guest's lock code between Install and Check-In, or between Check-Out and Removal will result in a log entry.  Additional alerting can be configured."
      input name: "installTime", type: "time", title: "What time is early Check-In? I.E. when do lock codes need to be installed? <small>(default: 11:00)</small>", required: false, default: "11:00"
      input name: "removeTime", type: "time", title: "What time is late Check-Out? I.E. when do lock codes need to be removed? <small>(default: 15:00)</small>", required: false, default: "15:00"
    }
    section(getFormat('line')){}
    section(getFormat('header-green','Additional notifications')) {
      input name: "pushover", type: "capability.notification", title: "Select your Pushover notification device, if installed.", required: false
      input name: "extendedTimeCodeUsage", type: "bool", title: "Do you want to receive notifications outside of the normal check-in/check-out times?", default: false
    }
  }
}

def testing() {
  dynamicPage(name:"", title: "", install:false, uninstall:false, nextPage: "testOutput") {
    section(getFormat('header-green','testing')){
      input name: "testDate", type: "string", title: "Test Date", required: true, submitOnChange: false
      input name: "testTime", type: "time", title: "Test Time", required: true, submitOnChange: false
      input name: "testOffset", type: "decimal", title: "Test Offset", submitOnChange: false
    }
  }
}
def testOutput() {
  dynamicPage(name:"", title: "", intstall:false, uninstall:false, nextPage: "mainPage") {
    section(getFormat('header-green','test output')){
      paragraph "${testDate}"
      paragraph "${testTime}"
      paragraph "${testOffset}"
      paragraph "${genCron(testDate,testTime,testOffset)}"
    }
  }
}

def installed() {
  log.info "Installed with settings: ${settings}"
  state.rsvnQueue = []
  initialize()
}

def updated() {
  if(logEnabled) log.debug "updated() with settings: ${settings}"
  unsubscribe()
  initialize()
}

def initialize() {
  subscribe(units, 'vacancy', vacancyHandler)
//  subscribe(units, 'arrivingGuest', arrivalHandler)
//  subscribe(units, 'leavingGuest', departureHandler)
  updateCrons()
  
}

def uninstalled() {}


// *********** Begin CRON Commands *********** //
String genCronOnce(dateTime) {
  DateTimeFormatter minDTF = DateTimeFormatter.ofPattern("m")
  DateTimeFormatter hrDTF = DateTimeFormatter.ofPattern("H")
  DateTimeFormatter dayDTF = DateTimeFormatter.ofPattern("dd")
  DateTimeFormatter monDTF = DateTimeFormatter.ofPattern("MMM")
  DateTimeFormatter yrDTF = DateTimeFormatter.ofPattern("yyyy")
  min = dateTime.format(minDTF)
  hr = dateTime.format(hrDTF)
  day = dateTime.format(dayDTF)
  yr = dateTime.format(yrDTF)
  return "0 ${Min} ${Hr} ${day} ${mon} ? ${Year}"
}

String genCronDaily(timeStr, offset) {
  // timeStr -> min and hr
  CharSequence csT = timeStr.substring(11,16)
  DateTimeFormatter tDTF = DateTimeFormatter.ofPattern("HH:mm")
  LocalTime time = LocalTime.parse(csT, tDTF)
  // change time using offset
  if(offset >= 0) {
    oM = offset % 60
    oH = (offset - oM) / 60
    time = time + Duration.ofMinutes(oM.toLong()) + Duration.ofHours(oH.toLong())
  } else {
    oM = (-1 * offset) % 60
    oH = ((-1 * offset) - oM) / 60
    time = time - Duration.ofMinutes(oM.toLong()) - Duration.ofHours(oH.toLong())
  }
  DateTimeFormatter minDTF = DateTimeFormatter.ofPattern("m")
  Min = time.format(minDTF)
  DateTimeFormatter hrDTF = DateTimeFormatter.ofPattern("H")
  Hr = time.format(hrDTF)
  
  return "0 ${Min} ${Hr} ? * * *"
}

String genCronToday(timeStr, offset) {
  // timeStr -> min and hr
  CharSequence csT = timeStr.substring(11,16)
  DateTimeFormatter tDTF = DateTimeFormatter.ofPattern("HH:mm")
  LocalTime time = LocalTime.parse(csT, tDTF)
  // change time using offset
  if(offset >= 0) {
    oM = offset % 60
    oH = (offset - oM) / 60
    time = time + Duration.ofMinutes(oM.toLong()) + Duration.ofHours(oH.toLong())
  } else {
    oM = (-1 * offset) % 60
    oH = ((-1 * offset) - oM) / 60
    time = time - Duration.ofMinutes(oM.toLong()) - Duration.ofHours(oH.toLong())
  }
  DateTimeFormatter minDTF = DateTimeFormatter.ofPattern("m")
  Min = time.format(minDTF)
  DateTimeFormatter hrDTF = DateTimeFormatter.ofPattern("H")
  Hr = time.format(hrDTF)
  
  
  
  return "0 ${Min} ${Hr} ? * * *"
}

def updateCrons() {
  // schedule setVacancy() on each child Unit device
  schedule(genCronDaily(installTime, -5), setUnitVacancies) //run setUnitVacancies 5 minutes before installTime
  
}
// *********** End CRON Commands *********** //


// *********** Begin Code Commands *********** //
def installCodes(rsvn) {
  position = 21 //update with proper code slot position
  code = rsvn.code
  name = rsvn.guestname
  locks.each {
    it.setCode(position, code, name)
  }
  runIn(30, verifyInstall, [data: rsvn])
  //if(extendedTimeCodeUsage) {
    //subscribe(locks, lockEvtHandler)
  //}
}

def verifyInstall(rsvn) {
  locks.each {
    if(it.currentValue("codeChanged") != "added") {
      log.warn "code installation may have failed on ${it.getLabel()} for ${rsvn}"
    }
  }
}

def removeCodes() {
  position = 21 //update with proper code slot position
  locks.each {
    it.deleteCode(position)
  }
  runIn(30, verifyRemove, [data: rsvn])
}

def verifyRemove(rsvn) {
  locks.each {
    if(it.currentValue("codeChanged") != "deleted") {
      log.warn "code removal may have failed on ${it.getLabel()} for ${rsvn}"
    }
  }
}
// *********** End Code Commands *********** //

// *********** Begin RSVN Commands *********** //
def processIncomingRsvn(rsvn) {
  //log.info "${listingCode} - ${rsvn}"
  tempRsvn = [:]
  if(rsvn.listing == listingCode) {
    rsvn.each {
     tempRsvn.put(it.key, it.value)
    }
    if(queueContains(rsvn)) { updateRsvnInQueue(rsvn) } else state.rsvnQueue.put(rsvn)
    if(logEnabled) log.info "Incoming Rsvn for ${listingCode}: ${rsvn}"
    units.each {
      if(logEnabled) log.info "Incoming Rsvn sent to ${it.getName()}"
      it.storeRsvn(tempRsvn)
    }
  }
}

void processCancelledRsv(rsvn) {
  if(rsvn.listing == listingCode) {
    units.each {
      it.removeRsvn(rsvn.rsvnKey)
    }
    removeRsvnFromQueue(rsvn.rsvnKey)
  }
}

boolean queueContains(rsvn) {
  if(!state.rsvnQueue) { return false }
  state.rsvnQueue.each { if( it.rsvnKey == rsvn.rsvnKey ) { return true } }
  return false
}

void sortRsvnQueue() {
  state.rsvnQueue.sort { a, b -> a.rsvnKey <=> b.rsvnKey }
}

void removeRsvnFromQueue(rsvnKey) {
  sortRsvnQueu()
  state.rsvnQueue.eachWithIndex { it, index -> if(it.rsvnKey == rsvnKey) { state.rsvnQueue.remove(index) }}
}

void updateRsvnInQueue(rsvn) {
  if(state.rsvnQueue) {
    state.rsvnQueue.each { if( it.rsvnKey == rsvn.rsvnKey && rsvn.sendTime.compareTo(its.sendTime)) it = rsvn }
  }
}

// *********** End RSVN Commands *********** //

def setUnitVacancies() {
  units.each {
    it.setVacancy()
  }
}

def vacancyHandler(evt) {
  unit = evt.getDevice()
  vacancy = evt.value
  switch (vacancy) {
    case "arriving":
      if(logEnabled) log.debug "${unit.getLabel()} - vacancy changed to arriving"
      runOnce(genCronOnce(timeToday(installTime)),installCodes)
      break
    case "leaving":
      if(logEnabled) log.debug "${unit.getLabel()} - vacancy changed to leaving"
      runOnce(genCronOnce(timeToday(removeTime)),removeCodes)
      break
    case "sameday":
      if(logEnabled) log.debug "${unit.getLabel()} - vacancy changed to sameday"
      runOnce(genCronOnce(timeToday(installTime)),installCodes)
      runOnce(genCronOnce(timeToday(removeTime)),removeCodes)
    default:
      if(logEnabled) log.debug "${unit.getLabel()} - vacancy unchanged"
      break
  }
}

def getFormat(type, myText='') {
    if (type == 'header-green') return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if (type == 'line') return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
    if (type == 'title') return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


