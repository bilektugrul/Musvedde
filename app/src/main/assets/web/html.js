import { h, Component } from 'preact';
import htm from 'htm';

export const html = htm.bind(h);

function shallowDiffers(a, b) {
  for (const key in a) if (a[key] !== b[key]) return true;
  for (const key in b) if (!(key in a)) return true;
  return false;
}

export function memo(render) {
  class Memo extends Component {
    shouldComponentUpdate(next) {
      return shallowDiffers(this.props, next);
    }

    render(props) {
      return render(props);
    }
  }
  Memo.displayName = `Memo(${render.name})`;
  return Memo;
}
