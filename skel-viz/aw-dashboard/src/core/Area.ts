export class Area {
  id: number;
  name: string;
  addr: string;
  counter: number;
  area: number;
  longitude: number;
  latitude: number;

  constructor(id: number, addr: string, center: [number, number]) {
    this.id = id;
    this.name = `radar-${id}`;
    this.addr = addr;
    this.counter = Math.floor(Math.random() * 10000);
    this.area = Math.floor(Math.random() * 100) / 10; // Random area between 0 and 10 km²
    [this.longitude, this.latitude] = center;
  }
}
