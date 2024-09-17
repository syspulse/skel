import React, { useState } from 'react';

function App() {
  const [colors, setColors] = useState(['#ff0000', '#00ff00', '#0000ff']);
  const [gradientCounts, setGradientCounts] = useState([5, 5, 5]);

  const generateGradients = (baseColor, count) => {
    const gradients = [];
    for (let i = 0; i < count; i++) {
      const opacity = (i + 1) / count;
      gradients.push(`${baseColor}${Math.round(opacity * 255).toString(16).padStart(2, '0')}`);
    }
    return gradients;
  };

  const handleColorChange = (index, newColor) => {
    const newColors = [...colors];
    newColors[index] = newColor;
    setColors(newColors);
  };

  const handleCountChange = (index, newCount) => {
    const newCounts = [...gradientCounts];
    newCounts[index] = parseInt(newCount);
    setGradientCounts(newCounts);
  };

  const allGradients = colors.map((color, index) => 
    generateGradients(color, gradientCounts[index])
  );

  const jsonStructure = JSON.stringify({
    gradients1: allGradients[0],
    gradients2: allGradients[1],
    gradients3: allGradients[2]
  }, null, 2);

  return (
    <div className="App">
      {[0, 1, 2].map((sectionIndex) => (
        <div key={sectionIndex} style={{marginBottom: '20px'}}>
          <input 
            type="color" 
            value={colors[sectionIndex]} 
            onChange={(e) => handleColorChange(sectionIndex, e.target.value)} 
          />
          <input 
            type="number" 
            value={gradientCounts[sectionIndex]} 
            min="2" 
            max="10" 
            onChange={(e) => handleCountChange(sectionIndex, e.target.value)} 
          />
          <div style={{ display: 'flex', height: '50px' }}>
            {generateGradients(colors[sectionIndex], gradientCounts[sectionIndex]).map((color, index) => (
              <div key={index} style={{ 
                flex: 1, 
                backgroundColor: color, 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                color: index < gradientCounts[sectionIndex] / 2 ? 'black' : 'white',
                fontWeight: 'bold'
              }}>
                {color.toUpperCase()}
              </div>
            ))}
          </div>
        </div>
      ))}
      <pre style={{ 
        backgroundColor: '#f0f0f0', 
        padding: '10px', 
        borderRadius: '5px',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all'
      }}>
        {jsonStructure}
      </pre>
    </div>
  );
}

export default App;