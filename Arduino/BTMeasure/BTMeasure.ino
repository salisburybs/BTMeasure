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
byte measureMask = 0;               // Measure & Transmit A0

unsigned long sampleSentMark = 0;    // Marks the time of the last analog read / transmission
unsigned long markToggleLED  = 0;    // Marks the time of last LED state change
unsigned long markTemp       = 0;    // Mark for temp storage of a time
unsigned long sampleCount    = 0;    // Number of samples received
unsigned long sampleMaxCount = 0;    // Number of samples to send

boolean compressOutput       = 0;    // Binary encode output data
boolean debug                = 0;    // Send debugging information to serial output
boolean waitingForInput      = false;
boolean ack                  = true; // Set high on ACK signal from Android

volatile unsigned int preCount = 0; // Set by programming command (120hz)

volatile unsigned int index = 0;
const int maxBuffer = 600;
byte msb[maxBuffer];
byte lsb[maxBuffer];

ISR(TIMER1_OVF_vect)
{
  TCNT1=preCount;
  PORTB = (PORTB & 0xDF) | (!(PORTB&0x20) << 5 ); // Clear LED output OR !LED_ON (gives a small status indicator)
  
  if(measureMask > 0){
    byte myMask = measureMask;
    for(int i=0; i<6; i++){
      struct Sample s;
      if(myMask & (1 << i)){ // compares measuremask to see if input is enabled
        compress(i, s.data);
        buf->add(buf, &s);
      }
      myMask &= (0 << i); // clear the measure bit from mask
      if(myMask == 0){ // break loop if done reading inputs
        break;
      }
    }
  }
}

void setup() {
  // initialize serial communication at 115200 baudrate bits per second
  Serial.begin(115200);
  nextState = stateNull;

  // Configure TIMER1 registers
  TCCR1A = 0x00;
  TCCR1B = (1 << CS11) | (1 << CS10); // 64 prescale = 16e6/64 hz = 250000 hz  
  TCNT1 = preCount;
  TIMSK1 |= (1 << TOIE0); // Turn on Timer1 overflow interrupt
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

      case 'K':
        ack = true;
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

void compress(int inputPin, byte value[]){
  int sensorValue = analogRead(inputPin);
  value[0] = sensorValue & 3;
  value[1] = sensorValue >> 2;
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

void send_buffer(int count){
  struct Sample s;
  byte i = 0;
  
  while (buf->pull(buf, &s))
  {
    Serial.write(s.data[0]);
    Serial.write(s.data[1]);
    Serial.write('\n');
    i++;
    if((count > 0) && (i > count)){
      break;
    }
  }
}

void loop() {
  if(!(buf->isEmpty(buf))){
    send_buffer(20); 
  }
  if(buf->isFull(buf)){
    noInterrupts();
    send_buffer(0);
    interrupts();
  }
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
        measureMask = 0;
        sampleCount = 0;
        sampleMaxCount = 0;
        stateEntryFlag = stateMeasure;
        //TIMSK1 = TIMSK1 & (0 << TOIE0); // Turn off TIMER 1 overflow event
        DDRB |= (1 << 5); // PIN13 SET DIRECTION TO OUTPUT
        pinMode(3, OUTPUT);
        analogWrite(3, 128);
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
      }
      
      // State change and configuration block
      tmpNextState = getNextState(currentState);
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
              preCount = atoi(cmd);
              TCNT1 = preCount;
              Serial.print("INFO preCount=");
              Serial.print(preCount);
              Serial.write('\n');
              break;
            case 'S':
              cmd++;
              // set mask for outputs
              measureMask = atoi(cmd);
              Serial.print("INFO measureMask=");
              Serial.print(measureMask); //must be != 0 for samples to buffer
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
        Serial.print(stateToggleLED);
        Serial.write('\n');
      }

      // Toggle LED every 1000ms
      markTemp = millis(); // Will introduce delay if micros is called twice
      if ((markTemp - markToggleLED) > 1000) {
        markToggleLED = markTemp;
        if (debug) {
          Serial.print("INFO ");
          Serial.print(!digitalRead(13));
          Serial.write('\n');
        }
        digitalWrite(13, !digitalRead(13));
      }

      nextState = getNextState(currentState);
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
