export class Area {
  id: number;
  name: string;
  addr: string;
  counter: number;
  radius: number;
  longitude: number;
  latitude: number;

  constructor(id: number, center: [number, number], radius: number) {
    this.id = id;
    this.name = `Hex ${id}`;
    this.addr = `Address ${id}`;
    this.counter = Math.floor(Math.random() * 10000);
    this.radius = radius;
    [this.longitude, this.latitude] = center;
  }
}
