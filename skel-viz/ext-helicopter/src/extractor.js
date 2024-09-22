

export async function getDashboard(ts0,ts1,projectId) {

  if(!projectId || projectId === ''){
      return {};
  }

  const url = `https://api.extractor.dev.hacken.cloud/api/v1/project/${projectId}/dashboard`;
  const payload = `{"from":${ts0},"to":${ts1},"interval":"1d","timezone":"Europe/Kiev","id":${projectId}}`
  
  try {
    const token = localStorage.getItem('jwtToken');
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: payload//JSON.stringify(payload),      
    });

    console.log("response:",response);
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log("data:",data);
    
    return data;

  } catch (error) {
    console.error('Error fetching data:', error);
    throw new Error('Error fetching data:', error);
  }
}

export async function getTenants() {
    const jwtToken = localStorage.getItem('jwtToken') || '';
    
    const payload = {
        from: 0,
        size: 1000,
        pageNumber: 1,
        where: 'status = "ACTIVE" AND name != "placeholder"',
        sort: [{field: "createdAt", order: "Desc"}]
    };

    try {
      const response = await fetch('https://api.extractor.dev.hacken.cloud/api/v1/tenant/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${jwtToken}`
        },
        body: JSON.stringify(payload)
      });
  
      if (response.ok) {
        const data = await response.json();
        
        return data.data || []; // Assuming the API returns an object with a 'tenants' array

      } else {
        console.error('Failed to fetch tenants');
        return [];
      }
    } catch (error) {
      console.error('Error fetching tenants:', error);
      return [];
    }
  }

export async function getProjects(tenantId) {
    const jwtToken = localStorage.getItem('jwtToken') || '';

    const payload = {
        size: 100,
        where: `tenantId = ${tenantId}`,
    };

    try {
      const response = await fetch(`https://api.extractor.dev.hacken.cloud/api/v1/project/search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${jwtToken}`
        },
        body: JSON.stringify(payload)
      });
  
      if (response.ok) {
        const data = await response.json();
        
        return data.data;

      } else {
        console.error('Failed to fetch project');
        return {};
      }
    } catch (error) {
      console.error('Error fetching project:', error);
      return {};
    }
  }