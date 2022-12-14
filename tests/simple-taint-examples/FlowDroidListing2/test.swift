class Data {
    var f : String;
    init() {
        self.f = "I'm not tainted (yet)";
    }
}

func taintIt(in1: String, out1: Data) {
    let x = out1;
    x.f = in1;
    sink(sunk: out1.f); //!testing!sink
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let p = Data();
let p2 = Data();

taintIt(in1: source(), out1: p); //!testing!source//!testing!source//!testing!source!fp
sink(sunk: p.f); //!testing!sink

taintIt(in1: "public", out1: p2);
sink(sunk: p2.f); //!testing!sink!fp // SWAN-43
