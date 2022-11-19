import React, { useState } from "react";

const LoginStateContext = React.createContext({
  state: "undefined state",
  setState: () => {}
});
export default LoginStateContext;

