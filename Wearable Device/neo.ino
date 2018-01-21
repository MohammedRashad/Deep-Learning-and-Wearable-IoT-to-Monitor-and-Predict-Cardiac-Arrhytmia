
#define USE_ARDUINO_INTERRUPTS true
#include <PulseSensorPlayground.h>


const int OUTPUT_TYPE = SERIAL_PLOTTER;


const int PIN_INPUT = A0;
const int PIN_BLINK = 13;    // Pin 13 is the on-board LED
const int PIN_FADE = 5;
const int THRESHOLD = 550;   // Adjust this number to avoid noise when idle


PulseSensorPlayground pulseSensor;

void setup() {
 
  Serial.begin(115200);

  

  pulseSensor.analogInput(PIN_INPUT);
  pulseSensor.blinkOnPulse(PIN_BLINK);
  pulseSensor.fadeOnPulse(PIN_FADE);

  pulseSensor.setSerial(Serial);
  pulseSensor.setOutputType(OUTPUT_TYPE);
  pulseSensor.setThreshold(THRESHOLD);

  
  if (!pulseSensor.begin()) {
    
    for(;;) {
      
      digitalWrite(PIN_BLINK, LOW);
      delay(50);
      digitalWrite(PIN_BLINK, HIGH);
      delay(50);
    }
  }
}

void loop() {
 
  delay(20);

  // write the latest sample to Serial.
 pulseSensor.outputSample();

  
  if (pulseSensor.sawStartOfBeat()) {
   pulseSensor.outputBeat();
  }
}
