metadata {
  definition (name: "ALCM-Unit-driver", namespace: "mepholdings", author: "Mark E Penzien") {
    capability "Actuator"
    
    singleThreaded: true
    
    command "manualAddRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Is this for a CURRENT reservation, the NEXT reservation to check in, or an existing reservation that is LEAVING today?"],
                              [name: "rsvnKey", type: "STRING", description: "A unique key string to identify the reservation; <small>\"manual\" is the default</small>", required:false, defaultValue:"manual"],
                              [name: "listing", type: "STRING", description: "The short code for the listing", required:true],
                        [name:"guestName", type:"STRING", description: "Guest Name"],
                        [name:"code", type:"NUMBER", description:"Guest's door code"],
                        [name:"start", type:"NUMBER", description:"Reservation start date in YYYYMMDD format"],
                        [name:"end", type:"NUMBER", description:"Reservation end date in YYYYMMDD format"]]
    command "manualExtendRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Extend the check-out date of a reservation.", defaultValue:"current"],
                           [name:"end", type:"NUMBER", description:"Reservation end date in YYYYMMDD format"]]
    command "manualRemoveRsvn", [[name: "rsvnPosition", type: "ENUM", constraints: ["current", "next", "leaving"], description: "Remove a RSVN record"]]
    command "moveNextToCurr"
    command "getVacancy"
    command "getCurrRsvn"
    command "getNextRsvn"
    command "storeRsvn"
    command "removeRsvn"
    
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
  log.info "${device.name} updated()"
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
  if (logEnable) "${device.name} setBlock() called: ${isBlocked == true}"
  if(isBlocked) {
    sendEvent(name: 'isBlocked', value: true, descriptionText: "${device.label} is BLOCKED!")
  } else {
    sendEvent(name: 'isBlocked', value: false, descriptionText: "${device.label} is NOT blocked.")
  }
  log.warn "${device.name} blocking is ${isBlocked == true}"
}

private setVacancy() {
  log.info "${device.name} setVacancy run..."
  def vStatus = ""
  if(isBlocked) {
    vStatus = "blocked"
  } else {
    if(state.currRsvn == null && state.nextRsvn == null) {
      //sendEvent(name: '', value: "", descriptionText: "")
      if(!device.currentValue("isBlocked")) {
        vStatus = "empty"
        sendEvent(name: 'vacancy', value: "${vStatus}", descriptionText: "${device.label} vacancy updated: ${vStatus}")
        device.deleteCurrentState('arrivingGuest')
        device.deleteCurrentState('currentGuest')
        device.deleteCurrentState('leavingGuest')
      } else {
        log.warn "${device.label} is blocked!"
        return
      }
    }
    def todayStr = new Date().format('YYYYMMdd')
    def today = todayStr.toLong()
    log.info "Today: ${today} & start: ${nextRsvn.start}"
    
    if(state.currRsvn != null && state.nextRsvn == null) {
      if(state.currRsvn.start == today) {
        vStatus = "arriving"
        sendEvent(name: 'arrivingGuest', value: "${state.currRsvn.guestName}", descriptionText: "${state.currRsvn.guestName} is arriving today to ${device.label}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.start < today && today < state.currRsvn.end) {
        vStatus = "occupied"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: "${state.currRsvn.guestName}")
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.end == today) {
        vStatus = "leaving"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "${state.currRsvn.guestName}", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.label}")
      } else if(state.currRsvn.end < today || state.currRsvn.start > today) {
        log.warn "${device.label} has an invalid currRsvn:"
        log.warn "${currRsvn}"
        //removeRsvn("current")
        //log.warn "${device.label} removing invalid currRsvn"
        //setVacancy()
      }
    } else if(state.currRsvn == null && state.nextRsvn != null) {
      if(state.nextRsvn.start == today) {
        log.info "test"
        vStatus = "arriving"
        sendEvent(name: 'arrivingGuest', value: "${state.nextRsvn.guestName}", descriptionText: "${state.nextRsvn.guestName} is arriving today to ${device.name}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
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
        sendEvent(name: 'arrivingGuest', value: "${state.currRsvn.guestName}", descriptionText: "${state.currRsvn.guestName} is arriving today to ${device.name}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: null)
      } else if(state.currRsvn.end == today == state.nextRsvn.start) {
        vStatus = "sameday"
        sendEvent(name: 'arrivingGuest', value: "${state.nextRsvn.guestName}", descriptionText: "${state.nextRsvn.guestName} is arriving today to ${device.name}")
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "${state.currRsvn.guestName}", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.name}")
      } else if(state.currRsvn.end == today) {
        vStatus = "leaving"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: null)
        sendEvent(name: 'leavingGuest', value: "${state.currRsvn.guestName}", descriptionText: "${state.currRsvn.guestName} is leaving today from ${device.name}")
      } else if( (state.currRsvn.start < today && today < state.currRsvn.end) && (today < state.nextRsvn.start) ) {
        vStatus = "occupied"
        sendEvent(name: 'arrivingGuest', value: null)
        sendEvent(name: 'currentGuest', value: "${state.currRsvn.guestName}")
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

// *********** Begin Manual Commands *********** //

void manualAddRsvn(String rPos, String rsvnKey, String listing, String guestName, code, start, end) {
  log.info "${device.label} manualAddRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  manualAddRsvn aborted for ${guestName}"
    return
  }
  if(rPos != "current" && rPos != "next") return

  if (logEnable) log.debug "manualAddRsvn - ${device.label} ${rPos}: ${guestName}"
  
  if(rPos == "current") {
    if(state.currRsvn) {
      //current rsvn exists
      if(key == state.currRsvn.key && guestName == state.currRsvn.guestName) {
        //update current rsvn
        state.currRsvn.listing = listing
        state.currRsvn.code = code
        state.currRsvn.start = start
        state.currRsvn.end = end
      } else {
        log.warn "manualAddRsvn - adding new currRsvn \'${guestName}\' that doesn't match existing currRsvn ${state.currRsvn.guestName}"
        return
      }
    } else {
      //new current rsvn
      TreeMap tempCurr = ["key" : key, "listing" : listing, "guestName":guestName, "code":code, "start":start, "end":end]
      state.currRsvn = tempCurr
    }
    return
  } else if(rPos == "next") {
    if(state.nextRsvn) {
      if(state.nextRsvn.start < start) {
        log.warn "manualAddRsvn - adding new nextRsvn \'${guestName}\' starts later (${start}) than existing nextRsvn \'${state.nextRsvn.guestName}\' (${nextRsvn.start})"
        return
      } else if(state.nextRsvn.key == key && state.nextRsvn.guestName == guestName) {
        state.nextRsvn.listing = listing
        state.nextRsvn.code = code
        state.nextRsvn.start = start
        state.nextRsvn.end = end
        log.warn "manualAddRsvn - updating info for nextRsvn \'${guestName}\'"
      } else {
        state.nextRsvn.key = key
        state.nextRsvn.listing = listing
        state.nextRsvn.guestName = guestName
        state.nextRsvn.code = code
        state.nextRsvn.start = start
        state.nextRsvn.end = end
        log.warn "manualAddRsvn - replacing existing nextRsvn \'${nextRsvn.guestName}\' with new nextRsvn ${guestName}"
      }
    } else {
      TreeMap tempNext = ["key" : key, "listing" : listing, "guestName":guestName, "code":code, "start":start, "end":end]
      state.nextRsvn = tempNext
      log.warn "manualAddRsvn - adding new nextRsvn \'${guestName}\'"
    }
  }
  setVacancy()
}

void manualExtendRsvn(String rPos, end) {
  log.info "${device.label} manualExtendRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  manualExtendRsvn aborted for ${guestName}"
    return
  }
  log.debug "manualExtendRsvn ${rPos} to ${end}"
  if(rPos == "current") {
    state.currRsvn.end = end
  } else if(rPos == "next") {
    state.nextRsvn.end = end
  }
  setVacancy()
}

void manualRemoveRsvn(String rPos) {
  log.info "${device.label} manualRemoveRsvn run..."
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  manualRemoveRsvn still allowed for ${rPos}"
  }
  if(rPos == "current") {
    state.remove("currRsvn")
//    state.currRsvn = null
  } else if (rPos == "next") {
    state.remove("nextRsvn")
  } else log.warn "manualRemoveRsvn trying to remove invalid rsvn ${rPos}"
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

// *********** End Manual Commands *********** //


// *********** Begin ALCM Commands *********** //

String getVacancy() {
  return state.vacancy
}

Map getCurrRsvn(String rsvnKey) {
  if(!state.currRsvn) {
    log.warn "getCurrRsvn failed: currRsvn does not exist."
    return [:]
  }
  if(key != currRsvn.key) {
    log.warn "getCurrRsvn failed: passed key (${key}) doesn't match currRsvn key (${currRsvn.key})"
    return [:]
  }
  return state.currRsvn
}

Map getNextRsvn() {
  if(state.nextRsvn) {
    return state.nextRsvn
  }
  return [:]
}

void storeRsvn(Map rsvn) {
  if(currRsvn && rsvn.rsvnKey == currRsvn.rsvnKey) {
    rsvn.put("rPos", "current")
    addRsvn(rsvn)
  } else {
    rsvn.put("rPos", "next")
    addRsvn(rsvn)
  }
}
void addRsvn(Map rsvn) {
  log.info "addRsvn called on ${device.name}"
  if(rsvn.rPos == "current") {
    if(rsvn.rsvnKey != state.currRsvn.rsvnKey) {
      log.warn "addRsvn attempting to overwrite existing currRsvn (${state.currRsvn.rsvnKey}) with different rsvn (${rsvnKey})"
      return
    }
    log.warn "addRsvn updating existing currRsvn with matching key ${rsvnKey}"
    state.currRsvn = rsvn
    return
  }
  if(rsvn.rPos == "next") {
    if(state.nextRsvn) {
      if(state.nextRsvn.start < rsvn.start) {
        log.warn "addRsvn attempting to add a nextRsvn (${rsvn.rsvnKey} starts ${rsvn.start}) that starts later than the existing nextRsvn (${nextRsvn.rsvnKey} starts ${nextRsvn.start})"
        return
      }
      if(state.nextRsvn.start == rsvn.start) {
        log.warn "addRsvn replacing existing nextRsvn (${state.nextRsvn}) with new nextRsvn (${rsvn})"
        state.nextRsvn = rsvn
        return
      }
      if(state.nextRsvn.start > rsvn.start) {
        log.warn "addRsvn replacing nextRsvn (${state.nextRsvn}) with new nextRsvn (${rsvn}) that starts sooner"
        state.nextRsvn = rsvn
        return
      }
    } else {
      state.nextRsvn = rsvn
    }
  }
  setVacancy()
}

void removeRsvn(rsvnKey) {
  if(isBlocked) {
    log.warn "${device.label} isBlocked!  removeRsvn still allowed for ${rPos}"
  }
  if(currRsvn && currRsvn.rsvnKey == rsvnKey) {
    manualRemoveRsvn("current")
  }
  if(nextRsvn && nextRsvn.rsvnKey == rsvnKey) {
    manualRemoveRsvn("next")
  }
}    

// *********** End ALCM Commands *********** //
