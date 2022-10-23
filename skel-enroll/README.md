# skel-enroll

Implements simple event driven asynchronous durable Flow (Workflow) where state is always on Server side 
and client should be as simple and stateless as possible. 

Based on on Akka FSM Persistance (lots of pain) but highly scalable since Flow actors can be destroyed and continued on demand

Supports both Events and Durable State. Latest is tested with Durable States

The design is overly complex for Registration to support other types of flows.

__Registraion Flow example__:

```
START -> EMAIL -> CONFIRM_EMAIL -> CREATE_USER -> FINISH
```

At definition implementation level each phase must be Acknowledged:

```
"START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
```

### Commad Line 

1. Create new flow

```
./run-enroll.sh command start
```

It returns ID of Enroll Session (e.g. 9c4312dc-285b-4fec-a99b-0e12289a1d5e)


2. Query flow state (summary)

```
./run-enroll.sh command 9c4312dc-285b-4fec-a99b-0e12289a1d5e
```

or (default action is __command__)

```
./run-enroll.sh 9c4312dc-285b-4fec-a99b-0e12289a1d5e

summary: 9c4312dc-285b-4fec-a99b-0e12289a1d5e: Some(Summary(9c4312dc-285b-4fec-a99b-0e12289a1d5e,EMAIL,Some(),None,None,None,1658060550774,1658060630066,false,None))
```

It is at phase __EMAIL__ and awaits submission of email to the Flow

3. Submit email

This can be done from another process/time because session is durable and will preserve its state:

```
./run-enroll.sh command email 9c4312dc-285b-4fec-a99b-0e12289a1d5e user-300@domain.org

Waiting for user to confirm email: Some(user-300@domain.org): token=Some(4671970)                                                              
phase1=CONFIRM_EMAIL: waiting external action ...
```

It is at phase __EMAIL__ and awaits submission of email to the Flow

4. Another email can be submitted as if user didn't confirm previous email and entered new email. New confirmation token will be generated


5. Confirm Email (with `confirmation token`)

With invalid `token`:

```
./run-enroll.sh command confirm 9c4312dc-285b-4fec-a99b-0e12289a1d5e INVALID_TOKEN_111111

[INFO] [i.s.s.e.EnrollFlow$@5b7a7f33 EnrollFlow.scala:141] error={}
akka.pattern.StatusReply$ErrorMessage: 9c4312dc-285b-4fec-a99b-0e12289a1d5e: Invalid Confirm token: 'INVALID_TOKEN_111111'
```

The session will stay at the same phase.

With valid `token`

```
./run-enroll.sh confirm 9c4312dc-285b-4fec-a99b-0e12289a1d5e 4671970

```

10. Queery enroll flow state (summary)

```
./run-enroll.sh 9c4312dc-285b-4fec-a99b-0e12289a1d5e

summary: 9c4312dc-285b-4fec-a99b-0e12289a1d5e: Some(Summary(9c4312dc-285b-4fec-a99b-0e12289a1d5e,FINISH_ACK,Some(),Some(user-300@domain.org),None,None,1658060550774,1658061840,true,None))
```
