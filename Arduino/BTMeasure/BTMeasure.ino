// Bryan Salisbury


// State definitions
const byte stateNull      = 0;
const byte stateTest      = 1;
const byte stateMain      = 2; // not used
const byte stateMeasure   = 3;
const byte stateControl   = 4;
const byte stateToggleLED = 5;
const byte stateEcho      = 6;
const byte modeProgram    = 7;

const byte maxControlStringLength = 25;
char bufferInput[maxControlStringLength + 1];

// State Control Variables
byte stateEntryFlag = 0;
byte nextState = stateNull;

// Measure State Control Variables
byte measureMask = B1;               // Measure & Transmit A0

unsigned int  sampleDelay    = 500;  // Sample delay in micros for transmission (2000 Hz = 500 micros)
unsigned long sampleSentMark = 0;    // Marks the time of the last analog read / transmission
unsigned long markToggleLED  = 0;    // Marks the time of last LED state change
unsigned long markTemp       = 0;    // Mark for temp storage of a time
unsigned long sampleCount    = 0;    // Number of samples received
unsigned long sampleMaxCount = 0;    // Number of samples to send

boolean compressOutput       = 0;    // Binary encode output data
boolean debug                = 0;    // Send debugging information to serial output
boolean waitingForInput      = false;


void setup() {
  // initialize serial communication at 115200 baudrate bits per second
  Serial.begin(115200);
  nextState = stateNull;
}

byte getNextState(byte state) {
  byte index = 0;

  if (Serial.available()) {

    // switch on Serial.read()
    // saves 8 bytes of SRAM rather than storing in variable
    switch (Serial.read()) {
      case 'A':
        return stateNull;
        break;

      case 'M':
        return stateMeasure;
        break;

      case 'C':
        return stateControl;
        break;

      case 'P':
        // All blocking code
        // Necessary at this time in order to read the entire string into buffer
        delay(100);
        if (Serial.available()) {
          while (Serial.available()) {
            // maxControlStringLength is defined max length of command string
            // does not include null character
            // mem overflow if index exceed this value.
            if (index > maxControlStringLength) {
              break;
            }
            bufferInput[index] = Serial.read();
            index++;
          }

          // Add null character to terminate bufferInput
          // index is last empty character or last character in array at this point
          bufferInput[index] = '\0';

          if (debug) {
            Serial.print("INFO ");
            Serial.print(bufferInput);
            Serial.write('\n');
          }
        }
        return modeProgram;
        break;

      case 'T':
        return stateTest;
        break;

      case 'L':
        return stateToggleLED;
        break;

      case 'E':
        return stateEcho;
        break;

      default:
        Serial.print("ERROR[");
        Serial.print(state);
        Serial.print("]: Command not recognized");
        Serial.write('\n');
    }
  }

  return state;
}

void serialWriteOptimized(int inputPin) {
  if (compressOutput) {
    int sensorValue = analogRead(inputPin);
    Serial.write(sensorValue & 3);
    Serial.write(sensorValue >> 2);
  } else {
    Serial.print(analogRead(inputPin));
    Serial.write('\n');
  }
}

void makePortsInputLow() {
  PORTD = PORTD & B11; // Set outputs 2-7 LOW
  PORTB = 0;           // Set outputs 8-13 LOW
  PORTC = 0;           // Set A0-A5 LOW

  DDRD = DDRD & B11; // Configure digital pins as inputs 2-7
  DDRB = 0;          // Configure pins 8-13 as inputs
  DDRC = 0;          // Configure pins A0-A5 as inputs
}

void loop() {
  byte tmpNextState = 0;
  byte index = 0;
  byte currentState = nextState;

  switch (currentState) {
    case stateNull:
      /*
      stateNull is the application initial state.
      Application waits for input from user.
      */
      if (stateEntryFlag != stateNull) {
        makePortsInputLow();
        stateEntryFlag = stateNull;
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
      }

      nextState = getNextState(currentState);
      break;

    case stateMeasure:
      /*
      Main execution state
      Controls timing / etc.
      */
      if (stateEntryFlag != stateMeasure) {
        //makePortsInputLow();
        measureMask = 0;
        sampleDelay = 0;
        sampleCount = 0;
        sampleMaxCount = 0;
        waitingForInput = true;
        stateEntryFlag = stateMeasure;
        pinMode(3, OUTPUT);
        analogWrite(3, 128);
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
      }

      // Measure / send data block
      if ((measureMask > 0) && !waitingForInput) {
        if ((micros() - sampleSentMark) > sampleDelay) {
          // use measureMask to determine inputs to send
          // odds are this stylistic choice will produce slight offset between measurements.
          // Modify serialWriteOptimized to take the "measureMask"?
          sampleSentMark = micros();
          if (measureMask & B1) {
            serialWriteOptimized(A0);
          }
          if (measureMask & B10) {
            serialWriteOptimized(A1);
          }
          if (measureMask & B100) {
            serialWriteOptimized(A2);
          }
          if (measureMask & B1000) {
            serialWriteOptimized(A3);
          }
          if (measureMask & B10000) {
            serialWriteOptimized(A4);
          }
          if (measureMask & B100000) {
            serialWriteOptimized(A5);
          }
          // Optimized write does not send newline
          // newline separates samples to reduce effect of time skew
          sampleCount += 1;
          if (compressOutput) {
            Serial.write('\n');
          }
          if (debug) {
            Serial.print("INFO sampleCount=");
            Serial.print(sampleCount);
            Serial.write('\n');
          }
        }
      }
      // State change and configuration block
      tmpNextState = getNextState(currentState);
      if ((sampleMaxCount > 0)&&(sampleCount > sampleMaxCount)){
        Serial.print("sampleMaxCount");
        Serial.write('\n');
        tmpNextState = stateNull;
      }
      if (tmpNextState == modeProgram) {
        Serial.print(bufferInput);
        Serial.write('\n');
        char* cmd = strtok(bufferInput, ":");
        while (cmd != 0) {
          switch (cmd[0]) {
            case 'C':
              cmd++; // skip first character
              // set global compression variable
              compressOutput = atoi(cmd);
              if (debug) {
                Serial.print("INFO compression=");
                Serial.print(compressOutput);
                Serial.write('\n');
              }
              break;
            case 'D':
              cmd++;
              // set delay value
              sampleDelay = atoi(cmd);
              Serial.print("INFO sampleDelay=");
              Serial.print(sampleDelay);
              Serial.write('\n');
              break;
            case 'S':
              cmd++;
              // set mask for outputs
              measureMask = atoi(cmd);
              Serial.print("INFO measureMask=");
              Serial.print(measureMask);
              Serial.write('\n');
              break;
            case 'N':
              cmd++;
              // set mask for outputs
              sampleMaxCount = atoi(cmd);
              Serial.print("INFO sampleMaxCount=");
              Serial.print(sampleMaxCount);
              Serial.write('\n');
              break;
          }
          cmd = strtok(0, ":");
          delay(100);
        }
        bufferInput[0] = '\0';

        // recognized an attempt at configuring output
        waitingForInput = false;
      } else {
        nextState = tmpNextState;
        // Break from current state
        break;
      }
      // end configuration block
      break;


    // Control State
    case stateControl:
      if (stateEntryFlag != stateControl) {
        makePortsInputLow();
        stateEntryFlag = stateControl;
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
      }

      nextState = getNextState(currentState);
      break;


    case stateTest:
      /*
      Code produced to test max bandwidth / sample rate possible
      */
      if (stateEntryFlag != stateTest) {
        stateEntryFlag = stateTest;
        pinMode(3, OUTPUT);
        analogWrite(3, 128);
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
      }
      serialWriteOptimized(A0);
      nextState = getNextState(currentState);
      break;


    // L
    case stateToggleLED:
      /*
       * Toggles LED on PIN 13 on interval sample delay
       */
      if (stateEntryFlag != stateToggleLED) {
        stateEntryFlag = stateToggleLED;
        makePortsInputLow();
        pinMode(13, OUTPUT);
        sampleDelay = 1000;
        Serial.print(stateToggleLED);
        Serial.write('\n');
      }

      // Toggle LED every period of sampleDelay
      markTemp = millis(); // Will introduce delay if micros is called twice
      if ((markTemp - markToggleLED) > sampleDelay) {
        markToggleLED = markTemp;
        if (debug) {
          Serial.print("INFO ");
          Serial.print(!digitalRead(13));
          Serial.write('\n');
        }
        digitalWrite(13, !digitalRead(13));
      }

      tmpNextState = getNextState(currentState);
      if (tmpNextState == modeProgram) {
        sampleDelay = atoi(bufferInput);
        Serial.print(sampleDelay);
        Serial.write('\n');
      } else {
        nextState = tmpNextState;
      }
      break;


    /*
     * stateEcho will echo serial input back to output
     * limited functionality, since many characters need to be recognized as commands
     */
    case stateEcho:
      if (stateEntryFlag != stateEcho) {
        stateEntryFlag = stateEcho;
        makePortsInputLow();
        Serial.print(stateEcho);
        Serial.write('\n');
      }

      tmpNextState = getNextState(currentState);
      if (tmpNextState == modeProgram) {
        Serial.print(bufferInput);
        Serial.write('\n');
      } else {
        nextState = tmpNextState;
      }
      break;

    default:
      nextState = stateNull;
      Serial.print("ERROR: Execution not defined!");
      Serial.write('\n');
  }
}
