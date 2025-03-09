metadata {
  definition (name: "AirBNB-LCM-Unit", namespace: "mepholdings", author: "Mark E Penzien") {
    capability "Actuator"
    
    singleThreaded: true
    
    command "addRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Is this for a CURRENT reservation, the NEXT reservation to check in, or an existing reservation that is LEAVING today?"],
                        [name:"guestName", type:"STRING", description: "Guest Name"],
                        [name:"code", type:"NUMBER", description:"Guest's door code"],
                        [name:"start", type:"NUMBER", description:"Reservation start date in YYYYMMDD format"],
                        [name:"end", type:"NUMBER", description:"Reservation end date in YYYYMMDD format"]]
    command "extendRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Extend the check-out date of a reservation.", defaultValue:"current"],
                           [name:"end", type:"NUMBER", description:"Reservation end date in YYYYMMDD format"]]
    command "removeRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Remove a RSVN record"]]
    command "moveNextToCurr"
    //command "resetError"
    //command "setError"

    //attribute "unitName", "string"
    
    //All Start and End attributes use this datestring format: yyyyMMdd
    attribute "vacancy", "enum", ["empty", "arriving", "occupied", "leaving", "sameday", "blocked"]
    attribute "currentGuest", "string"
    attribute "arrivingGuest", "string"
    attribute "leavingGuest", "string"
    //attribute "currRsvn", "string"
    //attribute "nextRsvn", "string"
  }

  preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true, description: " "
    input name: "isBlocked", type: "bool", title: "Block unit from receiving reservations?", defaultValue: false
  }
}

void logsOff(){
  log.warn "${device.label} debug logging disabled..."
  device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void installed() {
  initialize()
  log.warn "${device.label} installed()"
  runIn(1800,logsOff)
}

void updated() {
  initialize()
  log.info "${device.label} updated()"
  if (logEnable) runIn(1800,logsOff)
}

def initialize() {
  setBlock()
  setVacancy()
}

private isToday(int datestamp) {
  if (logEnable) log.info "isToday called..."
  def today = new Date().format('YYYYMMdd')
  log.info "${today}"
  def todayNum = today.toInteger()
  if(today == datestamp) {
    return true
  }
  return false
}

private vsToday(int datestamp) {
  if (logEnable) log.info "vsToday called..."
  def today = new Date().format('YYYYMMdd')
  log.info "${today}"
  def todayNum = today.toInteger()
  if(datestamp == today) {
    return 0                   // datestamp is today
  } else if(datestamp < today) {
    return -1                  // datestamp is before today
  } else return 1              // datestamp is after today
}

private setBlock() {
  if (logEnable) "${device.label} setBlock() called: ${isBlocked == true}"
  if(isBlocked) {
    sendEvent(name: 'isBlocked', value: true, descriptionText: "${device.label} is BLOCKED!")
  } else {
    sendEvent(name: 'isBlocked', value: false, descriptionText: "${device.label} is NOT blocked.")
  }
  log.warn "${device.label} blocking is ${isBlocked == true}"
}

private setVacancy() {
  log.info "${device.label} setVacancy run..."
  def vStatus = ""
  if(isBlocked) {
    vStatus = "blocked"
  } else {
    if(state.currRsvn == null && state.nextRsvn == null) {
      //sendEvent(name: '', value: "", descriptionText: "")
      if(!device.currentValue("isBlocked")) {
        vStatus = "empty"
        sendEvent(name: 'vacancy', value: "${vStatus}", descriptionText: "${device.label} vacancy updated: ${vStatus}")
      } else {
        log.warn "${device.label} is blocked!"
        return
      }
    }
    def todayStr = new Date().format('YYYYMMdd')
    def today = todayStr.toInteger()
    
    if(state.currRsvn != null && state.nextRsvn == null) {
      if(state.currRsvn.start == today) {
        vStatus = "arriving"
        sendEvent(name: 'arrivingGuest', value: "state.currRsvn.guestName", descriptionText: "${state.currRsvn.guestName} is arriving today to ${device.label}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.start < today && today < state.currRsvn.end) {
        vStatus = "occupied"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: "state.currRsvn.guestName")
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.end == today) {
        vStatus = "leaving"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "state.currRsvn.guestName", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.label}")
      } else if(state.currRsvn.end < today || state.currRsvn.start > today) {
        log.warn "${device.label} has an invalid currRsvn:"
        log.warn "${currRsvn}"
        //removeRsvn("current")
        //log.warn "${device.label} removing invalid currRsvn"
        //setVacancy()
      }
    } else if(state.currRsvn == null && state.nextRsvn != null) {
      if(state.nextRsvn.start == today) {
        endEvent(name: 'arrivingGuest', value: "state.nextRsvn.guestName", descriptionText: "${state.nextRsvn.guestName} is arriving today to ${device.label}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
        return
      } else if( (state.nextRsvn.start < today) || (state.nextRsvn.end < today) ) {
        log.warn "${device.label} has invalid nextRsvn:"
        log.warn "${nextRsvn}"
      }
    } else if(state.currRsvn != null && state.nextRsvn != null) {
  //    cRsvn = slurper.parseText(state.currRsvn)
  //    cRsvn.start = cRsvn.start.toInt()
  //    nRsvn = slurper.parseText(state.nextRsvn)
  //    nRsvn.start = nRsvn.start.toInt()
      if(state.currRsvn.start == today) {
        vStatus = "arriving"
        sendEvent(name: 'arrivingGuest', value: "state.currRsvn.guestName", descriptionText: "${state.currRsvn.guestName} is arriving today to ${device.label}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.end == today == state.nextRsvn.start) {
        vStatus = "sameday"
        sendEvent(name: 'arrivingGuest', value: "state.nextRsvn.guestName", descriptionText: "${state.nextRsvn.guestName} is arriving today to ${device.label}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "state.currRsvn.guestName", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.label}")
      } else if(state.currRsvn.end == today) {
        vStatus = "leaving"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "state.currRsvn.guestName", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.label}")
      } else if( (state.currRsvn.start < today && today < state.currRsvn.end) && (today < state.nextRsvn.start) ) {
        vStatus = "occupied"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: "state.currRsvn.guestName")
        sendEvent(name: 'leavingGuest', value: null)
      } else {
        log.warn "${device.label} has potential invalid currRsvn and nextRsvn:"
        log.warn "currRsvn: ${state.currRsvn}"
        log.warn "nextRsvn: ${state.nextRsvn}"
      }
    }
  }
  sendEvent(name: 'vacancy', value: "${vStatus}", descriptionText: "${device.label} vacancy updated: ${vStatus}")
}


void addRsvn(String rPos, String guestName, code, start, end) {
  log.info "${device.label} addRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  addRsvn aborted for ${guestName}"
    return
  }
  if(rPos != "current" && rPos != "next") return

  if (logEnable) log.debug "addRsvn - ${device.label} ${rPos}: ${guestName}"
  
  if(rPos == "current") {
    if(state.currRsvn) {
      //current rsvn exists
      if(guestName == state.currRsvn.guestName && start == state.currRsvn.start && code == state.currRsvn.code) {
        //update current rsvn
        state.currRsvn.end = end
      } else {
        log.warn "addRsvn - adding new currRsvn \'${guestName}\' that doesn't match existing currRsvn ${state.currRsvn.guestName}"
        return
      }
    } else {
      //new current rsvn
      TreeMap tempCurr = ["guestName":guestName, "code":code, "start":start, "end":end]
      state.currRsvn = tempCurr
      def myClass = getObjectClassName(state.currRsvn.start)
      log.warn "Added RSVN with start as ${myClass}"
    }
    return
  } else if(rPos == "next") {
    if(state.nextRsvn) {
      if(state.nextRsvn.start < start) {
        log.warn "addRsvn - adding new nextRsvn \'${guestName}\' starts later (${start}) than existing nextRsvn \'${state.nextRsvn.guestName}\' (${nextRsvn.start})"
        return
      } else if(state.nextRsvn.start == start && state.nextRsvn.guestName == guestName) {
        state.nextRsvn.guestName = guestName
        state.nextRsvn.code = code
        state.nextRsvn.start = start
        state.nextRsvn.end = end
        log.warn "addRsvn - updating info for nextRsvn \'${guestName}\'"
      } else {
        state.nextRsvn.guestName = guestName
        state.nextRsvn.code = code
        state.nextRsvn.start = start
        state.nextRsvn.end = end
        log.warn "addRsvn - replacing existing nextRsvn \'${nextRsvn.guestName}\' with new nextRsvn ${guestName}"
      }
    } else {
      TreeMap tempNext = ["guestName":guestName, "code":code, "start":start, "end":end]
      state.nextRsvn = tempNext
      log.warn "addRsvn - adding new nextRsvn \'${guestName}\'"
    }
  }
  setVacancy()
}

void extendRsvn(String rPos, end) {
  log.info "${device.label} extendRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  extendRsvn aborted for ${guestName}"
    return
  }
  log.debug "extendRsvn ${rPos} to ${end}"
  if(rPos == "current") {
    state.currRsvn.end = end
  } else if(rPos == "next") {
    state.nextRsvn.end = end
  }
  setVacancy()
}

void removeRsvn(String rPos) {
  log.info "${device.label} removeRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  removeRsvn still allowed for ${rPos}"
  }
  if(rPos == "current") {
    state.remove("currRsvn")
//    state.currRsvn = null
  } else if (rPos == "next") {
    state.remove("nextRsvn")
  } else log.warn "removeRsvn trying to remove invalid rsvn ${rPos}"
  setVacancy()
}

void moveNextToCurr() {
  log.info "${device.label} moveNextToCurr() run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked! moveNextToCurr aborted for ${state.nextRsvn.guestName}"
    return
  }
  if(state.currRsvn) {
    log.warn "${device.label} is trying to overwrite currRsvn with nextRsvn:"
    log.warn "currRsvn: ${state.currRsvn}"
    log.warn "nextRsvn: ${state.nextRsvn}"
    return
  }
  state.currRsvn = state.nextRsvn
  state.remove("nextRsvn")
  setVacancy()
}

Map getCurrRsvn() {
  if(state.currRsvn) {
    return state.currRsvn
  }
  return [:]
}

String getVacancy() {
  return state.vacancy
}


