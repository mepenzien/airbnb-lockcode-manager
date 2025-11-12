import java.time.format.DateTimeFormatter
import java.time.LocalTime

definition(
    name: "ALCM-Unit-app",
    namespace: "mepholdings",
    parent: "mepholdings:ALCM-Listing-app",
    author: "Mark E Penzien",
    description: "Manages per-unit settings, timing and lock-to-unit associations.",
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
  page name: 'locks', title: "Lock Associations & Settings", nextPage: 'mainPage', uninstall: true
  page name:"testing", title:"testing", uninstall:false, nextPage:"testingOutput"
  page name:"testOutput", title: "testOutput", uninstall:false, nextPage:"mainPage"

}