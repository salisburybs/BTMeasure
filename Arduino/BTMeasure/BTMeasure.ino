 // Bryan Salisbury


// State definitions
const byte stateNull      = 0;
const byte stateSendBuf   = 2;
const byte stateMeasure   = 3;
const byte stateControl   = 4;
const byte modeProgram    = 7;

// global
byte stateEntryFlag = -1;      // start execution in impossible state
byte nextState = stateNull;    // start state for code execution
boolean debug  = true;         // Enable debugging information
volatile unsigned int preCount = 0; // Set by programming command

// global timing
unsigned long markTemp  = 0; // Mark for temp storage of a time
unsigned long markControl  = 0; // Used in controlMode

// output buffer
const int maxBuffer = 700;
volatile unsigned int index = 0; // used in ISR for buffering stored samples
volatile byte buffer0[maxBuffer];
volatile byte buffer1[maxBuffer];

// stateMeasure
byte measureMask       = 0; // Measure & Transmit Mask
boolean compressOutput = 1; // Binary encode output data (default)

// stateSendBuf
unsigned int lastIndex = 0; // used to mark last index accessed in stateSendBuf
boolean ack = false;        // Set high on ACK signal from Android
byte ackTimeout = 100;      // timeout for transmission resend
byte errorCount = 0;        // transmission error count
const byte failCount = 255;  // Abort sending after XX resends

// modeProgram
const byte maxControlStringLength = 19;
char bufferInput[maxControlStringLength + 1];

// stateControl
const byte tSamp = 1000; // microseconds
double kp = 0.0,ki = 0.0,kd = 0.0;
int error = 0, output = 0, desiredPosition = 0, sumError = 0, minOut = 0, maxOut = 0;
byte outputPin = 0, inputPin = 0, directionPin = 0;
boolean start = false;
boolean directionPinPositive = LOW;
byte currentState;

ISR(TIMER1_OVF_vect)
{
  TCNT1=preCount;
 
  //PORTB |= (1 << 5); // PIN 13 LED ON
  if(measureMask > 0){
    // voodoo code: shifts one bit over each iteration to match the bits set in measureMask for each input
    for(byte i=0; i<5; i++){
      if(measureMask & (1 << i)){ // compares measuremask to see if input is enabled
        
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

void printState(){
  Serial.print("STATE=");
  Serial.print(currentState);
  Serial.write('\n');
}

byte getNextState(byte state) {
  byte pindex = 0;
  unsigned long markProgram = 0;

  if (Serial.available()) {

    // switch on Serial.read()
    
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
        pindex = 0;
        markProgram = millis();
        while (millis() - markProgram < 250){
          if (Serial.available()) {
            while (Serial.available()) {
              // maxControlStringLength is defined max length of command string
              // does not include null character
              // mem overflow if index exceed this value.
              if (pindex > maxControlStringLength) {
                break;
              }
              bufferInput[pindex] = Serial.read();
              if(bufferInput[pindex] == '\n'){
                break;
              }
              pindex++;
            }
            if(bufferInput[pindex] == '\n'){
              break;
            }
          }
          if(bufferInput[pindex] == '\n'){
            break;
          }
        }
        
        // Add null character to terminate bufferInput
        // index is last empty character or last character in array at this point
        bufferInput[pindex] = '\0';
        
        if (debug) {
          Serial.print("INFO ");
          Serial.print(bufferInput);
          Serial.write('\n');
        }        
        return modeProgram;
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
        
        while(Serial.available()){
          Serial.write(Serial.read());
        }
    }
    
  }

  return state;
}

int getPosition(){
  return analogRead(inputPin);
}

void doControl(){
  output = 0;  
  error = (desiredPosition - getPosition());  // Define error

  if(ki > 0){
    // Calculate I term of output
    output = (ki * (sumError * (tSamp / 1000000)));
  
    // Apply limits to control output
    if(output > maxOut){
      output = maxOut;
    }else if(output < minOut){
      output = minOut;
    } 
  }
  
  // kp gain added to output
  output += (kp * error);
  
  // Apply limits to control output
  if(output > maxOut){
    output = maxOut;
  }else if(output < minOut){
    output = minOut;
  }

  if(ki > 0){
    sumError += error;
  }else{
    sumError = 0;
  }
  
  // Set direction output and make output positive
  if(output >= 0){
    digitalWrite(directionPin, directionPinPositive);
  }else if(output < 0) {
    output *= -1; //make output value positive
    digitalWrite(directionPin, !directionPinPositive);
  }
  
  analogWrite(outputPin, output);  // Send PWM duty rate to motor actuator
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

  currentState = nextState;
  switch (currentState) {
    case stateNull:
      /*
      stateNull is the application initial state.
      Application waits for input from user.
      */
      if (stateEntryFlag != stateNull) {
        makePortsInputLow();
        printState();
        stateEntryFlag = stateNull;
        
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
        printState();

        // Reset timing variables
        start=false;
        preCount=0;

        measureMask = 0;
        DDRB |= (1 << 5); // PIN13 SET DIRECTION TO OUTPUT
              
        // Configure TIMER1 registers
        TCCR1A = 0x00;
        TCCR1B = (1 << CS11) | (1 << CS10); // 64 prescale = 16e6/64 hz = 250000 hz
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
              Serial.print("INFO compression=");
              Serial.print(compressOutput);
              Serial.write('\n');
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

      // if test not yet started, check to see if required params set and start test
      if(!start){
        if(preCount != 0 && measureMask != 0){
          start = true;
          TIMSK1 |= (1 << TOIE0); // Turn on Timer1 overflow interrupt
        }
      }
      
      // Switch to sending mode
      if(index >= maxBuffer){
        nextState = stateSendBuf;
      }
      
      if(nextState != currentState){
        // Exit Case
        TIMSK1 &= (0 << TOIE0); // Turn off Timer1 overflow interrupt
        start = false; // probably redundant
      }
      // end configuration block
      break;


    // Control State
    case stateControl:
      if (stateEntryFlag != stateControl) {
        makePortsInputLow();
        // stateControl Defaults
        kp = 0;
        ki = 0;
        kd = 0;
        sumError = 0;
        error = 0;
        output = 0;
        desiredPosition = 0;
        //tSamp = 2;
        minOut = 0;
        maxOut = 255;
        inputPin = 0;
        outputPin = 9;
        directionPin = 13;
        directionPinPositive = LOW;
        start = false;

        TCCR1A = 0x00;
        TCCR1B = (1 << CS11) | (1 << CS10); // 64 prescale = 16e6/64 hz = 250000 hz
        index=0;
        
        stateEntryFlag = stateControl;
        printState();
        preCount = 0;
      }
      
      if(start){
        if((micros() - markControl) >= tSamp){
          markControl=micros();
          doControl();
        }

        // we can get away with using markTemp here and in modeProgram because neither
        // is used at the same time and the consequences of screwing up the timing here are minimal
        if((millis() - markTemp) >= 100){
          markTemp=millis();
          Serial.print(getPosition());
          Serial.write('\n');
        }
      }

      // State change and configuration block
      tmpNextState = getNextState(currentState);
      if (tmpNextState == modeProgram) {
        //Serial.print("INFO input=");
        //Serial.print(bufferInput);
        //Serial.write('\n');
        char* cmd = strtok(bufferInput, ":P");
        while (cmd != 0) {
          switch (cmd[0]) {
            case 'A': //Kp
              cmd++;
              kp = atof(cmd);
              Serial.print("INFO kp=");
              Serial.print(kp);
              Serial.write('\n');
              break;

            case 'B': //Ki
              cmd++;
              ki = atof(cmd);
              Serial.print("INFO ki=");
              Serial.print(ki);
              Serial.write('\n');
              break;

            case 'C': // Kd
              cmd++;
              kd = atof(cmd);
              Serial.print("INFO kd=");
              Serial.print(kd);
              Serial.write('\n');
              break;
              
            case 'D':
              cmd++;
              desiredPosition = atoi(cmd);
              Serial.print("INFO desiredPosition=");
              Serial.print(desiredPosition);
              Serial.write('\n');
              break;

            case 'E':
              cmd++;
              preCount = atoi(cmd);
              Serial.print("INFO preCount=");
              Serial.print(preCount);
              Serial.write('\n');
              break;
              
            case 'H':
              cmd++;
              maxOut = atoi(cmd);
              Serial.print("INFO maxOut=");
              Serial.print(maxOut);
              Serial.write('\n');
              break;
            
            case 'L':
              cmd++;
              minOut = atoi(cmd);
              Serial.print("INFO minOut=");
              Serial.print(minOut);
              Serial.write('\n');
              break;
                          
            case 'O':
              cmd++;
              pinMode(outputPin, INPUT); // clear old outputPin
              outputPin = atoi(cmd);
              pinMode(outputPin, OUTPUT);
              Serial.print("INFO outputPin=");
              Serial.print(outputPin);
              Serial.write('\n');
              break;
            
            case 'I':
              cmd++;
              inputPin = atoi(cmd);
              Serial.print("INFO inputPin=");
              Serial.print(inputPin);
              Serial.write('\n');
              
              measureMask = 1 << inputPin; //set measuremask from desired input pin
              break;

            case 'S':
              start = true;
              Serial.print("INFO start\n");
              break;
          }
          cmd = strtok(0, ":P");
          delay(50);
          if(start){
            TIMSK1 |= (1 << TOIE0); // Turn on Timer1 overflow interrupt
          }
        }
        bufferInput[0] = '\0';
        
      } else {
        nextState = tmpNextState;
        
      }

      if(index >= maxBuffer){
        nextState = stateSendBuf;
        start = false; // probably redundant
      }

      // Exit Conditions 
      if(nextState != currentState){
        TIMSK1 &= (0 << TOIE0); // Turn off Timer1 overflow interrupt
        makePortsInputLow();
      }
      break;

    case stateSendBuf:
      if (stateEntryFlag != currentState) {
        stateEntryFlag = currentState;
        printState();
        makePortsInputLow();
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
          lastIndex++;
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
  }
}
