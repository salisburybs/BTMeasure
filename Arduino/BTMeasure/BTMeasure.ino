// Bryan Salisbury


// State definitions
const byte stateNull      = 0;
const byte stateTest      = 1;
const byte stateSendBuf   = 2;
const byte stateMeasure   = 3;
const byte stateControl   = 4;
const byte stateToggleLED = 5;
const byte stateEcho      = 6;
const byte modeProgram    = 7;

// global
byte stateEntryFlag = -1;
byte nextState = stateNull; // start state for code execution
boolean debug  = true;         // Enable debugging information
volatile unsigned int preCount = 0; // Set by programming command (120hz)

// global timing
unsigned long markTemp  = 0; // Mark for temp storage of a time
unsigned long markStart = 0; // Mark for temp storage of a time (ms)
unsigned long markStop  = 0; // Mark for temp storage of a time (ms)

// output buffer
const int maxBuffer = 750;
volatile unsigned int index = 0; // used in ISR for buffering stored samples
volatile byte buffer0[maxBuffer];
volatile byte buffer1[maxBuffer];

// stateMeasure
byte measureMask       = 0; // Measure & Transmit Mask
boolean compressOutput = 1; // Binary encode output data (default)

// stateLED
unsigned long markToggleLED = 0; // Marks the time of last LED state change

// stateSendBuf
unsigned int lastIndex = 0; // used to mark last index accessed in stateSendBuf
boolean ack = false;        // Set high on ACK signal from Android
byte ackTimeout = 100;      // timeout for transmission resend
byte errorCount = 0;        // transmission error count
const byte failCount = 100;  // Abort sending after XX resends

// modeProgram
const byte maxControlStringLength = 19;
char bufferInput[maxControlStringLength + 1];

ISR(TIMER1_OVF_vect)
{
  TCNT1=preCount;
  
  //PORTB |= (1 << 5); // PIN 13 LED ON
  if(measureMask > 0){
    byte myMask = measureMask;

    // voodoo code: shifts one bit over each iteration to match the bits set in measureMask for each input
    for(byte i=0; i<5; i++){
      if(myMask & (1 << i)){ // compares measuremask to see if input is enabled
        
        // Check to make sure we will not overflow buffer
        if(index < maxBuffer){
          int sensorValue = analogRead(i);
          buffer0[index] = sensorValue & 3;
          buffer1[index] = sensorValue >> 2;
          index++;
        }else{
          break;
        }
      }
      myMask &= (0 << i); // clears bit i from the measureMask
      if(myMask == 0){ // break loop when nothing is left to measure
        break;
      }
    }
  }
  //PORTB &= (0 << 5); // PIN 13 LED OFF
}

void setup() {
    // initialize serial communication at 115200 baudrate bits per second
  Serial.begin(115200);
  nextState = stateNull;

  for(int i = 0; i < maxBuffer-1; i++){
    buffer0[i] = 0;
    buffer1[i] = 0;
  }
}

byte getNextState(byte state) {
  byte pindex = 0;

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
            if (pindex > maxControlStringLength) {
              break;
            }
            bufferInput[pindex] = Serial.read();
            pindex++;
          }

          // Add null character to terminate bufferInput
          // index is last empty character or last character in array at this point
          bufferInput[pindex] = '\0';

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

      case 'S':
        return stateSendBuf;
        break;

      case 'K':
        ack = true;
        while (Serial.available()) {
          if(Serial.peek() == 'K'){
            Serial.read(); // discard duplicated ack command
          }
        }
        Serial.flush(); // waits for outgoing transmission to complete.
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

void makePortsInputLow() {
  PORTD = PORTD & B11; // Set outputs 2-7 LOW
  PORTB = 0;           // Set outputs 8-13 LOW
  PORTC = 0;           // Set A0-A5 LOW

  DDRD = DDRD & B11; // Configure digital pins as inputs 2-7
  DDRB = 0;          // Configure pins 8-13 as inputs
  DDRC = 0;          // Configure pins A0-A5 as inputs
}

void sendSample(byte b0, byte b1){
  Serial.write(b0);
  Serial.write(b1);
  Serial.write('\n');
}

void loop() {
  byte tmpNextState = 0;
  byte currentState = nextState;
  
  switch (currentState) {
    case stateNull:
      /*
      stateNull is the application initial state.
      Application waits for input from user.
      */
      if (stateEntryFlag != stateNull) {
        //makePortsInputLow();
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
        stateEntryFlag = stateMeasure;
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
        
        measureMask = 0;
        DDRB |= (1 << 5); // PIN13 SET DIRECTION TO OUTPUT
              
        // Configure TIMER1 registers
        TCCR1A = 0x00;
        TCCR1B = (1 << CS11) | (1 << CS10); // 64 prescale = 16e6/64 hz = 250000 hz  
        TIMSK1 |= (1 << TOIE0); // Turn on Timer1 overflow interrupt
        // TCNT1 = preCount; // set precount only once the value has been sent to board
        index=0;
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
          }
          cmd = strtok(0, ":");
          delay(100);
        }
        bufferInput[0] = '\0';
        
      } else {
        nextState = tmpNextState;
      }

      // if index > 0 and test start has not been recorded yet
      if(index > 0 && !markStart){
        if(!markStart){
          markStart = millis();
        }
      }
      
      // Switch to sending mode
      if(index >= maxBuffer - 1){
        nextState = stateSendBuf;
      }
      
      if(nextState != currentState){
        // Exit Case
        TIMSK1 &= (0 << TOIE0); // Turn off Timer1 overflow interrupt
        markStop = millis();
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

    case stateSendBuf:
      if (stateEntryFlag != currentState) {
        stateEntryFlag = currentState;
        Serial.print("STATE=");
        Serial.print(currentState);
        Serial.write('\n');
        ack = true;
        lastIndex = 0;
        errorCount = 0;
        markTemp = millis();
      }

      if(ack){
        sendSample(buffer0[lastIndex], buffer1[lastIndex]);
        lastIndex++;
        ack = false;
        markTemp = millis();
      }else{
        if((millis() - markTemp) > ackTimeout){
          sendSample(buffer0[lastIndex], buffer1[lastIndex]);
          markTemp = millis();
          errorCount++;
        }
      }
      
      nextState = getNextState(currentState);
      if(lastIndex >= maxBuffer){
        nextState = stateNull;
      }
      
      if(errorCount >= failCount){
        nextState = stateNull;
      }
      
      
      if(nextState != currentState){
        // Exit Conditions
        Serial.print("INFO SENT=");
        Serial.print(lastIndex);
        Serial.write('\n');
        Serial.print("INFO ERRORCOUNT=");
        Serial.print(errorCount);
        Serial.write('\n');
        break;
      }
      break;

    default:
      nextState = stateNull;
      Serial.print("ERROR: Execution not defined!");
      Serial.write('\n');
  }
}
